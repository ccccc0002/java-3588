from __future__ import annotations

from pathlib import Path
import platform
from typing import Any, Optional


class RknnSupportError(RuntimeError):
    pass


class RKNNLiteSession:
    def __init__(self, engine: Any):
        self.engine = engine

    @classmethod
    def open(
        cls,
        model_path: Any,
        core_mask: str = 'auto',
        target: Optional[str] = None,
        device_id: Optional[str] = None,
    ) -> 'RKNNLiteSession':
        try:
            from rknnlite.api import RKNNLite
        except ImportError as exc:
            raise RknnSupportError('rknn_toolkit_lite2 is not installed') from exc

        engine = RKNNLite()
        ret = engine.load_rknn(str(Path(model_path).resolve()))
        if ret != 0:
            raise RknnSupportError(f'RKNNLite.load_rknn failed: {ret}')

        init_kwargs = {}
        resolved_core_mask = resolve_core_mask(RKNNLite, core_mask)
        if resolved_core_mask is not None:
            init_kwargs['core_mask'] = resolved_core_mask
        on_board_linux = platform.system() == 'Linux' and platform.machine() == 'aarch64'
        if target and not on_board_linux:
            init_kwargs['target'] = target
        if device_id and not on_board_linux:
            init_kwargs['device_id'] = device_id

        ret = engine.init_runtime(**init_kwargs)
        if ret != 0:
            raise RknnSupportError(f'RKNNLite.init_runtime failed: {ret}')
        return cls(engine)

    def infer(self, inputs: list[Any], data_format: Optional[list[str]] = None, inputs_pass_through: Optional[list[int]] = None) -> Any:
        kwargs = {'inputs': inputs}
        if data_format is not None:
            kwargs['data_format'] = data_format
        if inputs_pass_through is not None:
            kwargs['inputs_pass_through'] = inputs_pass_through
        outputs = self.engine.inference(**kwargs)
        if outputs is None:
            raise RknnSupportError('RKNNLite.inference returned no outputs')
        return outputs

    def release(self) -> None:
        release_fn = getattr(self.engine, 'release', None)
        if callable(release_fn):
            release_fn()


def resolve_core_mask(rknn_lite_cls: Any, core_mask: str) -> Optional[Any]:
    value = str(core_mask or 'auto').strip().lower()
    mapping = {
        'auto': getattr(rknn_lite_cls, 'NPU_CORE_AUTO', None),
        'core_0': getattr(rknn_lite_cls, 'NPU_CORE_0', None),
        'core_1': getattr(rknn_lite_cls, 'NPU_CORE_1', None),
        'core_2': getattr(rknn_lite_cls, 'NPU_CORE_2', None),
        'core_0_1_2': getattr(rknn_lite_cls, 'NPU_CORE_0_1_2', None),
    }
    return mapping.get(value, mapping.get('auto'))
