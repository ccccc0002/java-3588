from __future__ import annotations

import importlib.util
import json
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, Dict, Optional, Tuple


PLUGIN_SCHEMA_VERSION = 'rknn.plugin.v1'
EXPECTED_PLUGIN_RUNTIME = 'rk3588_rknn'


class PluginRuntimeError(RuntimeError):
    def __init__(self, message: str, status_code: int = 502):
        super().__init__(message)
        self.status_code = status_code


@dataclass(frozen=True)
class PluginPackageContext:
    plugin_id: str
    version: str
    runtime: str
    root_dir: Path
    manifest_path: Path
    manifest: Dict[str, Any]
    config_path: Path
    config: Dict[str, Any]
    model_path: Path
    capabilities: list[str]


class PluginPackage:
    def __init__(
        self,
        context: PluginPackageContext,
        infer_fn: Callable[..., Any],
        postprocess_fn: Callable[..., Any],
        load_fn: Optional[Callable[..., Any]] = None,
        cleanup_fn: Optional[Callable[..., Any]] = None,
    ):
        self.context = context
        self._infer_fn = infer_fn
        self._postprocess_fn = postprocess_fn
        self._load_fn = load_fn
        self._cleanup_fn = cleanup_fn
        self._runtime_state: Any = None
        self._loaded = False
        self._load_count = 0

    def ensure_loaded(self) -> Any:
        if self._loaded:
            return self._runtime_state
        self._runtime_state = self._load_fn(self.context) if callable(self._load_fn) else {}
        self._loaded = True
        self._load_count += 1
        return self._runtime_state

    def execute(self, request_payload: Dict[str, Any], runtime_plan: Dict[str, Any]) -> Dict[str, Any]:
        runtime_state = self.ensure_loaded()
        started_at = time.perf_counter()
        raw_outputs = self._infer_fn(request_payload, runtime_plan, self.context, runtime_state)
        postprocessed = self._postprocess_fn(raw_outputs, request_payload, runtime_plan, self.context, runtime_state)
        elapsed_ms = max(1, int((time.perf_counter() - started_at) * 1000))
        normalized = normalize_postprocess_result(postprocessed)
        plugin_meta = dict(normalized.get('plugin_meta', {}))
        plugin_meta.setdefault('plugin_id', self.context.plugin_id)
        plugin_meta.setdefault('version', self.context.version)
        plugin_meta.setdefault('runtime', self.context.runtime)
        plugin_meta.setdefault('load_count', self._load_count)
        normalized['plugin_meta'] = plugin_meta
        normalized.setdefault('latency_ms', elapsed_ms)
        return normalized

    def summary(self) -> Dict[str, Any]:
        return {
            'plugin_id': self.context.plugin_id,
            'version': self.context.version,
            'runtime': self.context.runtime,
            'capabilities': list(self.context.capabilities),
            'config_path': str(self.context.config_path),
            'model_path': str(self.context.model_path),
            'loaded': self._loaded,
            'load_count': self._load_count,
        }

    def close(self) -> None:
        if callable(self._cleanup_fn) and self._loaded:
            self._cleanup_fn(self._runtime_state, self.context)
        self._loaded = False
        self._runtime_state = None


class PluginPackageManager:
    def __init__(self, plugins_root: Any, expected_runtime: str = EXPECTED_PLUGIN_RUNTIME, default_plugin_id: str = ''):
        self.plugins_root = Path(plugins_root).resolve() if plugins_root else None
        self.expected_runtime = str(expected_runtime or EXPECTED_PLUGIN_RUNTIME).strip() or EXPECTED_PLUGIN_RUNTIME
        self.default_plugin_id = str(default_plugin_id or '').strip()
        self._packages: Dict[str, PluginPackage] = {}
        self._errors: Dict[str, str] = {}
        self.refresh()

    def refresh(self) -> None:
        packages: Dict[str, PluginPackage] = {}
        errors: Dict[str, str] = {}
        if self.plugins_root is None or not self.plugins_root.exists():
            self._packages = {}
            self._errors = {}
            return
        for manifest_path in sorted(self.plugins_root.glob('*/manifest.json')):
            try:
                package = self._load_package(manifest_path)
                packages[package.context.plugin_id] = package
            except PluginRuntimeError as exc:
                errors[manifest_path.parent.name] = str(exc)
        self._packages = packages
        self._errors = errors

    def has_plugins(self) -> bool:
        return bool(self._packages)

    def inventory(self) -> Dict[str, Any]:
        return {
            'plugins_root': '' if self.plugins_root is None else str(self.plugins_root),
            'default_plugin_id': self.default_plugin_id,
            'plugins': [package.summary() for package in self._packages.values()],
            'errors': dict(self._errors),
        }

    def resolve(self, request_payload: Dict[str, Any]) -> Optional[PluginPackage]:
        plugin_id, explicitly_requested = self._extract_plugin_id(request_payload)
        if plugin_id:
            package = self._packages.get(plugin_id)
            if package is not None:
                return package
            if plugin_id in self._errors:
                raise PluginRuntimeError(f'requested plugin is invalid: {plugin_id}: {self._errors[plugin_id]}', status_code=503)
            raise PluginRuntimeError(f'requested plugin is not available: {plugin_id}', status_code=503)
        if explicitly_requested:
            raise PluginRuntimeError('plugin route requested but plugin_id is missing', status_code=400)
        if not self.default_plugin_id:
            return None
        default_package = self._packages.get(self.default_plugin_id)
        if default_package is not None:
            return default_package
        if self.default_plugin_id in self._errors:
            raise PluginRuntimeError(
                f'default plugin is invalid: {self.default_plugin_id}: {self._errors[self.default_plugin_id]}',
                status_code=503,
            )
        raise PluginRuntimeError(f'default plugin is not available: {self.default_plugin_id}', status_code=503)

    def execute(self, package: PluginPackage, request_payload: Dict[str, Any], runtime_plan: Dict[str, Any]) -> Dict[str, Any]:
        if package is None:
            raise PluginRuntimeError('plugin package is required', status_code=500)
        try:
            return package.execute(request_payload, runtime_plan)
        except PluginRuntimeError:
            raise
        except Exception as exc:
            raise PluginRuntimeError(f'plugin execution failed for {package.context.plugin_id}: {exc}', status_code=502) from exc

    def _load_package(self, manifest_path: Path) -> PluginPackage:
        root_dir = manifest_path.parent
        manifest = load_json_file(manifest_path, description='plugin manifest')
        plugin_id = as_non_empty_string(manifest.get('plugin_id'), 'plugin_id')
        version = as_non_empty_string(manifest.get('version'), 'version')
        schema_version = as_non_empty_string(manifest.get('schema_version'), 'schema_version')
        if schema_version != PLUGIN_SCHEMA_VERSION:
            raise PluginRuntimeError(f'{plugin_id}: unsupported schema_version {schema_version}')
        runtime = as_non_empty_string(manifest.get('runtime'), 'runtime')
        if runtime != self.expected_runtime:
            raise PluginRuntimeError(f'{plugin_id}: runtime must be {self.expected_runtime}, got {runtime}')
        capabilities = normalize_capabilities(manifest.get('capabilities'))
        if 'inference' not in capabilities:
            raise PluginRuntimeError(f'{plugin_id}: capabilities must include inference')

        assets = manifest.get('assets') if isinstance(manifest.get('assets'), dict) else {}
        config_path = resolve_required_file(root_dir, assets.get('config'), plugin_id, 'config')
        model_path = resolve_required_file(root_dir, assets.get('model'), plugin_id, 'model')
        config = load_json_file(config_path, description=f'{plugin_id} config')
        entrypoints = manifest.get('entrypoints') if isinstance(manifest.get('entrypoints'), dict) else {}
        infer_fn = load_entrypoint(root_dir, entrypoints.get('infer'), plugin_id, 'infer')
        postprocess_fn = load_entrypoint(root_dir, entrypoints.get('postprocess'), plugin_id, 'postprocess')
        load_fn = load_entrypoint(root_dir, entrypoints.get('load'), plugin_id, 'load', required=False)
        cleanup_fn = load_entrypoint(root_dir, entrypoints.get('cleanup'), plugin_id, 'cleanup', required=False)
        context = PluginPackageContext(
            plugin_id=plugin_id,
            version=version,
            runtime=runtime,
            root_dir=root_dir,
            manifest_path=manifest_path,
            manifest=manifest,
            config_path=config_path,
            config=config,
            model_path=model_path,
            capabilities=capabilities,
        )
        return PluginPackage(context=context, infer_fn=infer_fn, postprocess_fn=postprocess_fn, load_fn=load_fn, cleanup_fn=cleanup_fn)

    def _extract_plugin_id(self, request_payload: Dict[str, Any]) -> Tuple[str, bool]:
        payload = request_payload if isinstance(request_payload, dict) else {}
        plugin_route = payload.get('plugin_route') if isinstance(payload.get('plugin_route'), dict) else {}
        route_plugin = plugin_route.get('plugin') if isinstance(plugin_route.get('plugin'), dict) else {}
        route_selector = plugin_route.get('selector') if isinstance(plugin_route.get('selector'), dict) else {}
        candidates = [
            route_plugin.get('plugin_id'),
            route_selector.get('plugin_id'),
            payload.get('plugin_id'),
            payload.get('algorithm_package'),
        ]
        for value in candidates:
            text = str(value or '').strip()
            if text:
                return text, True
        registrations = [
            route_plugin.get('registration_id'),
            route_selector.get('registration_id'),
            payload.get('plugin_registration_id'),
            payload.get('registration_id'),
        ]
        for value in registrations:
            text = plugin_id_from_registration(value)
            if text:
                return text, True
        return '', to_bool(plugin_route.get('requested'), False)


def normalize_postprocess_result(value: Any) -> Dict[str, Any]:
    if isinstance(value, dict):
        result = dict(value)
    elif isinstance(value, list):
        result = {'detections': value}
    else:
        result = {'detections': []}
    detections = result.get('detections')
    alerts = result.get('alerts')
    events = result.get('events')
    plugin_meta = result.get('plugin_meta')
    attributes = result.get('attributes')
    result['detections'] = detections if isinstance(detections, list) else []
    result['alerts'] = alerts if isinstance(alerts, list) else []
    result['events'] = events if isinstance(events, list) else []
    result['plugin_meta'] = plugin_meta if isinstance(plugin_meta, dict) else {}
    result['attributes'] = attributes if isinstance(attributes, dict) else {}
    return result


def normalize_capabilities(value: Any) -> list[str]:
    raw_values = value if isinstance(value, list) else ([] if value is None else str(value).split(','))
    capabilities = []
    for item in raw_values:
        text = str(item).strip()
        if text and text not in capabilities:
            capabilities.append(text)
    return capabilities


def load_entrypoint(root_dir: Path, entrypoint: Any, plugin_id: str, role: str, required: bool = True) -> Optional[Callable[..., Any]]:
    if entrypoint in (None, ''):
        if required:
            raise PluginRuntimeError(f'{plugin_id}: entrypoint {role} is required')
        return None
    module_name, function_name = parse_entrypoint(str(entrypoint).strip(), plugin_id, role)
    module_path = (root_dir / module_name).resolve()
    if not module_path.exists() or not module_path.is_file():
        raise PluginRuntimeError(f'{plugin_id}: entrypoint module for {role} does not exist: {module_name}')
    dynamic_module_name = f'rk3588_plugin_{safe_identifier(plugin_id)}_{safe_identifier(role)}'
    spec = importlib.util.spec_from_file_location(dynamic_module_name, module_path)
    if spec is None or spec.loader is None:
        raise PluginRuntimeError(f'{plugin_id}: failed to load entrypoint module for {role}: {module_name}')
    module = importlib.util.module_from_spec(spec)
    sys.modules[dynamic_module_name] = module
    spec.loader.exec_module(module)
    fn = getattr(module, function_name, None)
    if not callable(fn):
        raise PluginRuntimeError(f'{plugin_id}: entrypoint function for {role} is missing: {function_name}')
    return fn


def resolve_required_file(root_dir: Path, relative_path: Any, plugin_id: str, field_name: str) -> Path:
    text = str(relative_path or '').strip()
    if not text:
        raise PluginRuntimeError(f'{plugin_id}: assets.{field_name} is required')
    resolved = (root_dir / text).resolve()
    if not resolved.exists() or not resolved.is_file():
        raise PluginRuntimeError(f'{plugin_id}: assets.{field_name} does not exist: {text}')
    return resolved


def load_json_file(path: Path, description: str) -> Dict[str, Any]:
    try:
        payload = json.loads(path.read_text(encoding='utf-8-sig'))
    except FileNotFoundError as exc:
        raise PluginRuntimeError(f'{description} is missing: {path}') from exc
    except json.JSONDecodeError as exc:
        raise PluginRuntimeError(f'{description} is invalid json: {path}') from exc
    if not isinstance(payload, dict):
        raise PluginRuntimeError(f'{description} must be a json object: {path}')
    return payload


def parse_entrypoint(value: str, plugin_id: str, role: str) -> Tuple[str, str]:
    if ':' not in value:
        raise PluginRuntimeError(f'{plugin_id}: entrypoint for {role} must be in module.py:function format')
    module_name, function_name = value.split(':', 1)
    module_name = module_name.strip()
    function_name = function_name.strip()
    if not module_name or not function_name:
        raise PluginRuntimeError(f'{plugin_id}: entrypoint for {role} must include module and function name')
    return module_name, function_name


def as_non_empty_string(value: Any, field_name: str) -> str:
    text = str(value or '').strip()
    if not text:
        raise PluginRuntimeError(f'{field_name} is required')
    return text


def safe_identifier(value: str) -> str:
    return ''.join(ch if ch.isalnum() else '_' for ch in str(value or '').strip())


def plugin_id_from_registration(value: Any) -> str:
    text = str(value or '').strip()
    if ':' in text:
        return text.split(':', 1)[0].strip()
    return text


def to_bool(value: Any, default: bool) -> bool:
    if isinstance(value, bool):
        return value
    if value is None:
        return default
    text = str(value).strip().lower()
    if text in ('1', 'true', 'yes', 'y', 'on'):
        return True
    if text in ('0', 'false', 'no', 'n', 'off'):
        return False
    return default
