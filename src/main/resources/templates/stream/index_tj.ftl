<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <title>闂傚倸鍊峰ù鍥х暦閻㈢绐楅柟鎵閸嬶繝鏌曟径鍫濆壔婵炴垶菤閺€浠嬫倵閿濆啫濡烽柛瀣崌瀹曟帒顭ㄩ崟顐わ紡濠电娀娼ч崐鎼佸焵椤掑倸娅忔繛鍏煎姍閺岀喎鐣￠悧鍫濇缂備焦顨堥崰鏍€佸☉姗嗘僵妞ゆ挾鍋愰崑鎾存償閵婏腹鎷?/title>
    <link href="/static/component/pear/css/pear.css" rel="stylesheet" />
    <style>
        :root {
            --dash-bg: #f4f7fb;
            --card-bg: #ffffff;
            --card-border: #e6ebf2;
            --text-main: #1f2d3d;
            --text-sub: #6b7785;
            --primary: #2b6cf6;
            --success: #16a34a;
            --warn: #ef4444;
        }
        body.pear-container { background: var(--dash-bg); transition: background-color .2s ease, color .2s ease; }
        body.pear-container.theme-light {
            --dash-bg: #f4f7fb;
            --card-bg: #ffffff;
            --card-border: #e6ebf2;
            --text-main: #1f2d3d;
            --text-sub: #6b7785;
            --primary: #2b6cf6;
            --success: #16a34a;
            --warn: #ef4444;
        }
        body.pear-container.theme-dark {
            --dash-bg: #111827;
            --card-bg: #1f2937;
            --card-border: #374151;
            --text-main: #e5e7eb;
            --text-sub: #9ca3af;
            --primary: #60a5fa;
            --success: #22c55e;
            --warn: #f87171;
        }
        body.pear-container.theme-cyber {
            --dash-bg: #07131f;
            --card-bg: #0f2538;
            --card-border: #17456a;
            --text-main: #d9f7ff;
            --text-sub: #8bbcd6;
            --primary: #22d3ee;
            --success: #10b981;
            --warn: #fb7185;
        }
        body.pear-container.theme-apple {
            --dash-bg: #f5f5f7;
            --card-bg: #ffffff;
            --card-border: #d2d2d7;
            --text-main: #1d1d1f;
            --text-sub: #6e6e73;
            --primary: #0071e3;
            --success: #34c759;
            --warn: #ff3b30;
        }
        .dashboard-shell { min-height: calc(100vh - 20px); display: flex; flex-direction: column; gap: 10px; }
        .global-toolbar {
            display: flex;
            justify-content: flex-end;
            align-items: center;
            gap: 8px;
            background: var(--card-bg);
            border: 1px solid var(--card-border);
            border-radius: 8px;
            padding: 8px 10px;
        }
        .global-toolbar label {
            color: var(--text-sub);
            font-size: 12px;
            margin-right: 2px;
        }
        .global-toolbar .layui-input-inline {
            width: 160px;
        }
        .global-toolbar .layui-input,
        .global-toolbar .layui-select-title input {
            height: 32px;
            line-height: 32px;
            border-radius: 6px;
            background: var(--card-bg);
            border-color: var(--card-border);
            color: var(--text-main);
        }
        .overview-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; }
        .overview-card { background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 8px; padding: 12px 14px; }
        .overview-label { color: var(--text-sub); font-size: 13px; margin-bottom: 8px; }
        .overview-value { color: var(--text-main); font-size: 28px; font-weight: 700; line-height: 1; }
        .overview-extra { margin-top: 8px; color: var(--text-sub); font-size: 12px; }
        .scheduler-hint-card { padding: 10px 14px; }
        .scheduler-status-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
        .scheduler-status-label { color: var(--text-sub); font-size: 12px; }
        .scheduler-status-badge { font-size: 12px; line-height: 1; padding: 4px 8px; border-radius: 999px; border: 1px solid transparent; }
        .scheduler-status-ok { color: var(--success); border-color: rgba(22,163,74,0.35); background: rgba(22,163,74,0.08); }
        .scheduler-status-degraded { color: var(--warn); border-color: rgba(239,68,68,0.35); background: rgba(239,68,68,0.08); }
        .scheduler-metrics { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
        .scheduler-metric { border: 1px dashed var(--card-border); border-radius: 8px; padding: 10px; }
        .scheduler-metric-label { color: var(--text-sub); font-size: 12px; margin-bottom: 8px; }
        .scheduler-metric-value { color: var(--text-main); font-size: 22px; font-weight: 700; line-height: 1; }
        .scheduler-metric-extra { color: var(--text-sub); font-size: 12px; margin-top: 8px; }

        .main-grid { display: grid; grid-template-columns: minmax(0, 1fr) 320px; gap: 10px; flex: 1; min-height: 0; }
        .card-box { background: var(--card-bg); border: 1px solid var(--card-border); border-radius: 8px; }
        .video-card { padding: 10px; display: flex; flex-direction: column; min-height: 0; }
        .stream-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
        .stream-nav { display: flex; align-items: center; }
        .stream-nav-title { padding-right: 10px; font-weight: 700; font-size: 16px; color: var(--text-main); }
        .stream-btn-group .layui-btn { min-width: 54px; }
        .stream-btn-active { background: var(--primary) !important; border-color: var(--primary) !important; color: #fff !important; }

        .stream-panel {
            background: linear-gradient(180deg, #12243f 0%, #1f3f69 100%);
            display: flex;
            justify-content: center;
            align-items: center;
            font-size: 16px;
            color: #d6e8ff;
            border-radius: 6px;
            border: 1px solid #2c4f7f;
        }
        .video-list-wrapper { flex: 1; min-height: 0; }
        .video-list-wrapper .layui-row { margin: 0 !important; }
        .video-list-wrapper .layui-row > div { padding: 0 !important; margin-bottom: 8px; }

        .alarm-card { padding: 10px; display: flex; flex-direction: column; min-height: 0; }
        .video-alarm-tit { font-size: 16px; font-weight: 700; color: var(--text-main); margin-bottom: 8px; }
        .video-alarm-counter { font-weight: 700; font-size: 14px; color: var(--text-main); margin-bottom: 8px; }
        .video-alarm-scroll-list { flex: 1; min-height: 0; overflow: auto; border-top: 1px solid var(--card-border); padding-top: 6px; }
        .alarm-box { margin: 0; padding: 8px; border: 1px solid #edf1f7; border-radius: 6px; margin-bottom: 8px; cursor: pointer; transition: border-color .2s ease, box-shadow .2s ease; }
        .alarm-box:hover { border-color: #93c5fd; box-shadow: 0 6px 14px rgba(43,108,246,0.12); }
        .alarm-img { width: 100%; height: 150px; margin-bottom: 8px; position: relative; background: #0f172a; }
        .alarm-tit { font-size: 13px; color: var(--text-sub); line-height: 1.5; margin: 0; padding: 0; }
        .alarm-mask { position: absolute; border: 1px solid #ef4444; color: #ef4444; font-size: 12px; background: rgba(255,255,255,0.35); }
        .alarm_counter { text-decoration: underline; font-size: 18px; color: var(--warn); }
        .alarm-detail-drawer { padding: 12px; max-height: calc(100vh - 90px); overflow: auto; }
        .alarm-detail-head { color: var(--text-main); font-size: 14px; font-weight: 700; margin-bottom: 10px; }
        .alarm-detail-meta { color: var(--text-sub); font-size: 12px; line-height: 1.8; margin-bottom: 10px; }
        .alarm-detail-img-wrap { background: #0f172a; border-radius: 6px; border: 1px solid #243042; padding: 6px; margin-bottom: 10px; }
        .alarm-detail-img { width: 100%; max-height: 320px; object-fit: contain; }
        .alarm-detect-table { width: 100%; border-collapse: collapse; }
        .alarm-detect-table th, .alarm-detect-table td { border: 1px solid var(--card-border); padding: 6px; font-size: 12px; color: var(--text-main); }
        .alarm-detect-table th { background: rgba(0,0,0,0.04); font-weight: 700; }

        .charts-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
        .chart-card { padding: 10px; }
        .chart-title { font-size: 15px; font-weight: 700; color: var(--text-main); margin-bottom: 8px; }
        .chart-canvas { height: 230px; }

        .rel { position: relative; }
        .cv { position: absolute; top: 4px; left: 4px; border: 0; }
        .stop-btn { position: absolute; right: 8px; bottom: 8px; color: #f87171; }
        .stop-btn a { color: #f87171; }
        .stop-btn a i { font-size: 20px; }
        .frame { position: absolute; left: 0; top: 0; width: 0; height: 0; border: 1px solid #ff4d4f; color: #ff4d4f; font-size: 12px; background: rgba(255,255,255,0.4); }

        @media (max-width: 1400px) {
            .overview-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
            .main-grid { grid-template-columns: minmax(0, 1fr) 300px; }
        }
        @media (max-width: 1024px) {
            .main-grid { grid-template-columns: minmax(0, 1fr); }
            .scheduler-metrics { grid-template-columns: minmax(0, 1fr); }
            .charts-grid { grid-template-columns: minmax(0, 1fr); }
            .video-list-wrapper { min-height: 420px; }
        }
    </style>
</head>
<body class="pear-container theme-light">
<div class="dashboard-shell">
    <div class="global-toolbar">
        <label for="theme-switch" data-i18n="global.theme">濠电姷鏁搁崑鐐哄垂閸洖绠伴柟闂磋閳ь剨绠撻幃婊勬叏閹般劌浜?/label>
        <div class="layui-input-inline">
            <select id="theme-switch" lay-ignore>
                <option value="light" data-i18n="theme.light">濠电姷鏁搁崑娑㈡偤閵娧冨灊闁绘顕х粣妤佺箾閹寸偟鎳呴柣?/option>
                <option value="dark" data-i18n="theme.dark">闂傚倸鍊风粈渚€骞栭鈶芥稑鈻庨幘鎵佸亾閸愨晛绶為悗锝庡墰瑜?/option>
                <option value="cyber" data-i18n="theme.cyber">缂傚倸鍊搁崐椋庣矆娓氣偓钘濆ù鍏兼綑閸ㄥ倿鏌ょ粙鍨倎缂佽妫涢幉鎼佸籍閸繄顔嗛梺鐟扮摠缁洪箖寮ㄦ禒瀣€甸柨娑樺船鐎氼厼鈻嶉弬搴撴斀闁挎稑瀚禒鈺傘亜椤愩埄妯€鐎规洘娲樼缓鐣岀矙閹稿孩袣?/option>
                <option value="apple" data-i18n="theme.apple">闂傚倸鍊风粈渚€宕ョ€ｎ€綁骞掑Δ瀣◤婵犮垼鍩栭崝鏍磹鐠轰警鐔嗛悹楦挎閻忓崬鈽夐幘宕囆ч柡灞界Х椤т線鏌涢幘瀵告创闁诡垪鍋撳銈呯箰閻楀﹪寮插┑瀣厱閻忕偠顕ч埀顒佺墵钘?/option>
            </select>
        </div>
        <label for="lang-switch" data-i18n="global.language">闂傚倷娴囧畷鍨叏閺夋嚚褰掑磼閻愯尙鐓戦梺閫炲苯澧撮柡?/label>
        <div class="layui-input-inline">
            <select id="lang-switch" lay-ignore>
                <option value="zh-CN">缂傚倸鍊搁崐鐑芥嚄閼稿灚鍙忔い鎾卞灩绾惧鏌熼幑鎰滅憸鐗堝笚閺呮煡鏌涢埄鍏狀亪鎯岄鈧娲偂鎼达絼绮甸梺鎼炲妼閻忔繈顢?/option>
                <option value="zh-TW">缂傚倸鍊搁崐宄懊归崶顒夋晩濠电姴瀚鑺ユ叏濡灝鐓愰柛瀣枛閺屾洘绻濊箛鎿冩喘闂佸磭绮褰掑Φ閸曨垰绠婚悹铏瑰劋閻庨箖姊?/option>
                <option value="en">English</option>
            </select>
        </div>
    </div>
    <div class="overview-grid">
        <div class="overview-card">
            <div class="overview-label">婵犵數濮烽弫鎼佸磻濞戙埄鏁嬫い鎾跺枑閸欏繘鏌熺紒銏犳灍闁哄懏绻堥弻鏇熷緞閸繂澹斿┑鐐村灟閸ㄥ綊鎮″☉銏＄厱婵炴垵宕獮鏍磼閹邦厾鈯曠紒缁樼箞閹粙妫冨ù韬插灲閺岀喎鐣￠柇锕€鍓堕悗?/div>
            <div class="overview-value" id="overview-today">${(counter)!'0'}</div>
            <div class="overview-extra">闂傚倷娴囧畷鐢稿窗閹邦喖鍨濋幖娣灪濞呯姵淇婇妶鍛櫣缂佺姳鍗抽弻娑樷槈濮楀牊鏁惧┑鐐叉噽婵炩偓闁哄矉绲借灒婵炲棙鍎冲▓顓犵磽娓氬洤浜滅紒澶婄秺瀵寮撮悢椋庣獮濠电偞鍨崹娲敁瀹ュ鈷?<span id="overview-date">-</span></div>
        </div>
        <div class="overview-card">
            <div class="overview-label">闂傚倸鍊搁崐椋庢濮橆剦鐒介柤濮愬€栫€氬鏌ｉ弮鍌氬付缂佲偓婢舵劕绠规繛锝庡墮婵″ジ鏌涘顒傜Ш妤犵偞鐗曡彁妞ゆ巻鍋撳┑陇鍋愮槐鎺楁偐閾忣偁浠㈤梺鍝勬湰閻╊垰顕ｉ幘顔嘉╅柕澶堝€楅惄搴ㄦ⒒娴ｅ憡鍟為柟鎼佺畺瀹曘垼顦归柍?/div>
            <div class="overview-value" id="overview-online">0</div>
            <div class="overview-extra">闂傚倸鍊搁崐宄懊归崶顒夋晪闁哄稁鍘肩粈鍫熸叏濮楀棗鍔﹂柨婵嗩槸缁犺櫕淇婇妶鍕槮闁告捁顫夋穱濠囧Χ韫囨洖鍩岄梺鍝ュ櫏閸嬪﹤顕?<span id="overview-total-camera">0</span></div>
        </div>
        <div class="overview-card">
            <div class="overview-label">缂傚倸鍊搁崐鎼佸磹閻戣姤鍤勯柤绋跨仛閸欏繘鏌ｉ姀鈩冨仩闁逞屽墮閸熸潙鐣烽崡鐐╂瀻闁归偊鍓欓獮鎴︽⒒娴ｇ顥忛柛瀣噹鐓ら柡宥庡幖閸戠姴霉閿濆牜鍤夌憸鐗堝笒缁€鍌炴煕韫囨艾浜圭紒瀣搐閳?/div>
            <div class="overview-value" id="overview-algorithm">0</div>
            <div class="overview-extra">闂傚倸鍊搁崐椋庣矆娓氣偓楠炲鏁撻悩鍐蹭画濡炪倖鐗滈崑娑㈠垂閸岀偞鐓曟い鎰剁稻缁€鈧梺绋胯閸旀垿寮婚弴銏犻唶婵犻潧娲ょ粣娑氱磼閹冣挃闁稿鎹囨俊鐢稿礋椤栨碍顥濋梺鍓茬厛閸犳帡骞愰崘顔解拺闁告繂瀚敍宥夋煕閹惧瓨鐨戦柟骞垮灩閳藉鈻庤箛鏇犵嵁闂佽鍑界紞鍡涘磻閸℃蛋鍥Ψ閳哄倻鍘?/div>
        </div>
        <div class="overview-card">
            <div class="overview-label">濠电姷鏁告慨鐑姐€傞挊澹╋綁宕ㄩ弶鎴濈€銈呯箰閻楀棝鎮為崹顐犱簻闁瑰搫妫楁禍鍓х磼閸撗嗘闁告瑥鍟村畷娲焵椤掍降浜滈柟鐑樺灥閳ь剚鎮傚畷銏⑩偓鐢电《閸嬫挸鈻撻崹顔界彯闂佺顑呴幊鎰板焵椤掑嫭娑ч柕鍫㈩焾椤曪綁宕奸弴鐐殿吅闂佺粯鍨靛Λ鏃堟偨?/div>
            <div class="overview-value" id="overview-model">0</div>
            <div class="overview-extra">闂傚倷娴囬褍霉閻戣棄绠犻柟鎹愵嚙缁犵喖姊介崶顒€桅闁圭増婢樼粈鍐┿亜閺囩偞鍣洪柡鍜佷邯濮婄粯绗熼崶褍顫╃紓浣割槸椤曨厾鍒掗崼銉ョ＜闁绘劕顕崢顏呯節閵忕姴甯ㄩ柡鍛箘閹广垹鈹戦崼鐕佸仺濠殿喗锕╅崜娑㈠汲?/div>
        </div>
    </div>

    <div class="card-box scheduler-hint-card">
        <div class="chart-title" id="scheduler-title">Scheduler Feedback</div>
        <div class="scheduler-status-row">
            <span class="scheduler-status-label" id="scheduler-status-label">Telemetry</span>
            <span class="scheduler-status-badge scheduler-status-ok" id="scheduler-status-badge" data-status="ok">OK</span>
        </div>
        <div class="scheduler-metric-extra" id="scheduler-error-extra" data-value="">Telemetry Error: none</div>
        <div class="scheduler-metrics">
            <div class="scheduler-metric">
                <div class="scheduler-metric-label" id="scheduler-pressure-label">Concurrency Pressure</div>
                <div class="scheduler-metric-value" id="scheduler-pressure">1.00</div>
                <div class="scheduler-metric-extra" id="scheduler-level-extra">Concurrency Level: 0</div>
            </div>
            <div class="scheduler-metric">
                <div class="scheduler-metric-label" id="scheduler-stride-label">Recommended Frame Stride</div>
                <div class="scheduler-metric-value" id="scheduler-stride">1</div>
                <div class="scheduler-metric-extra" id="scheduler-source-extra">Source: scheduler_feedback</div>
            </div>
            <div class="scheduler-metric">
                <div class="scheduler-metric-label" id="scheduler-dispatch-label">Suggested Min Dispatch (ms)</div>
                <div class="scheduler-metric-value" id="scheduler-dispatch">1000</div>
                <div class="scheduler-metric-extra" id="scheduler-cooldown-extra">Max Effective Cooldown: 0 ms</div>
            </div>
        </div>
    </div>

    <div class="main-grid">
        <div class="card-box video-card">
            <div class="stream-header">
                <div class="stream-nav">
                    <div class="stream-nav-title">闂傚倸鍊搁崐鐑芥嚄閸洖纾块柣銏㈩焾閻ょ偓绻濇繝鍌滃闁搞劌鍊块弻娑㈠箻閼碱剙濡介梺鍝勬缁捇寮婚妶澶婄濞达綀顫夐柨顓犵磽娴ｇ顣抽柛瀣枛楠炲绮欏▎鎯ф倯婵犮垼娉涢鍛村礈?/div>
                    <div class="layui-btn-group stream-btn-group" id="btns">
                        <button type="button" class="layui-btn layui-btn-primary" data-grid="1" onclick="show(1);">1闂傚倸鍊峰ù鍥敋瑜嶉～婵嬫晝閸屻倖鏅梺鍝勭▉閸樿偐澹曟繝姘厱婵炴垶顭囬幗鐘绘煕?/button>
                        <button type="button" class="layui-btn layui-btn-primary" data-grid="4" onclick="show(4);">4闂傚倸鍊峰ù鍥敋瑜嶉～婵嬫晝閸屻倖鏅梺鍝勭▉閸樿偐澹曟繝姘厱婵炴垶顭囬幗鐘绘煕?/button>
                        <button type="button" class="layui-btn layui-btn-primary" data-grid="9" onclick="show(9);">9闂傚倸鍊峰ù鍥敋瑜嶉～婵嬫晝閸屻倖鏅梺鍝勭▉閸樿偐澹曟繝姘厱婵炴垶顭囬幗鐘绘煕?/button>
                        <button type="button" class="layui-btn layui-btn-primary" data-grid="16" onclick="show(16);">16闂傚倸鍊峰ù鍥敋瑜嶉～婵嬫晝閸屻倖鏅梺鍝勭▉閸樿偐澹曟繝姘厱婵炴垶顭囬幗鐘绘煕?/button>
                    </div>
                </div>
                <div>
                    <a href="javascript:void(0)" class="layui-btn layui-btn-normal layui-btn-sm" onclick="handleStatics();">缂傚倸鍊搁崐鎼佸磹閹间礁纾归柣鎴ｅГ閸ゅ嫰鏌涢锝嗙缂佹劖顨婇弻锟犲炊閵夈儳鍔撮梺杞扮濞差參寮婚悢鍏尖拻閻庣數顭堟俊浠嬫⒑缂佹ɑ灏ㄩ柛瀣崌閺岋絾鎯旈敍鍕殯闂佺楠稿畷顒傚弲闂侀潧艌閺呮盯宕?/a>
                </div>
            </div>
            <div class="statics-items" id="statics-items" style="display:none;"></div>
            <div class="video-list-wrapper" id="video-list-wrapper">
                <div class="layui-row" id="video-list"></div>
            </div>
        </div>

        <div class="card-box alarm-card">
            <div class="video-alarm-tit">闂傚倸鍊搁崐椋庣矆娓氣偓楠炴牠顢曢敂钘変罕闂佸憡鍔﹂崰鏍婵犳碍鐓欓柛鎾楀懎绗￠梺缁樻尰閻╊垶寮诲☉姘勃闁告挆鈧慨鍥╃磽娴ｈ棄鐓愰柣鎿勭節瀵鎮㈤悜妯虹彴閻熸粌绻掓竟鏇㈠锤濡や胶鍘?/div>
            <div class="video-alarm-counter">婵犵數濮烽弫鎼佸磻濞戙埄鏁嬫い鎾跺枑閸欏繘鏌熺紒銏犳灍闁哄懏绻堥弻鏇熷緞閸繂澹斿┑鐐村灟閸ㄥ綊鎮″☉銏＄厱婵炴垵宕獮鏍磼閹邦厾鈯曠紒缁樼箞閹粙妫冨ù韬插灲閺岀喎鐣￠柇锕€鍓堕悗瑙勬礃缁诲牆鐣烽妸褉鍋撳☉娆樼劷闁告ê鎲＄换娑欐綇閸撗冨煂闂佹悶鍨鸿ぐ鍐偩閻㈢骞㈡繛鎴炵懅閸? <a href="javascript:void(0);" onclick="handleAlarmList();" class="alarm_counter"><span id="alarm-counter">${(counter)!'0'}</span></a></div>
            <div class="video-alarm-scroll-list">
                <div class="wrap" id="alarm-list">
                    <ul id="alarm-ul-list">
                        <#if reportList??>
                            <#list reportList as item>
                                <li class="alarm-item" onclick="openAlarmDetailById('${(item.id)!''}');">
                                    <div class="alarm-box">
                                        <div class="alarm-img" id="alarm_${(item.id)!''}">
                                            <img src="/report/stream?id=${(item.id)!''}" style="width: 100%; height: 100%; object-fit: contain;" params="${(item.params)!''}" onload="handleImgLoad(this, '${(item.id)!''}');" />
                                        </div>
                                        <div class="alarm-tit">闂傚倸鍊搁崐椋庣矆娓氣偓楠炲鏁嶉崟顓犵厯闂佺鎻梽鍕疾濠靛鐓忓┑鐐靛亾濞呮捇鏌℃担鍛婎棦闁哄矉缍佸顕€宕堕…鎴滃摋闂佽绻愮换鎺楀极婵犳艾绠栫憸鐗堝笒閻愬﹥銇勮箛鎾愁伀婵絻鍨藉娲焻閻愯尪瀚板褌鍗抽弻娑滅疀閺冩捁鈧法鈧?{(item.algorithmName)!''}</div>
                                        <div class="alarm-tit">闂傚倸鍊搁崐椋庣矆娴ｉ潻鑰块梺顒€绉查埀顒€鍊圭粋鎺斺偓锝庝簽閿涙盯姊洪悷鏉库挃缂侇噮鍨跺畷鎰板醇閺囩喓鍘介梺瑙勫婢ф鈽夎闇夐柣娆忔噽閻ｇ敻鏌″畝鈧崰鏍х暦閿濆棗绶炲┑鐘插€归幊娆撴煟鎼淬値娼愭繛鍙夛耿楠炴垶鎷呴搹閫涚瑝濠电偞鍨崹鍦尵瀹ュ鐓冪憸婊堝礈濞戙垺鍤嶉柛妤冨剱濞尖晠鏌ら崫銉︽毄闁?{(item.cameraName)!''}</div>
                                        <div class="alarm-tit">闂傚倸鍊搁崐椋庣矆娓氣偓楠炲鏁撻悩鍐蹭罕闂佸搫娲㈤崹鍦不鐟欏嫨浜滈柟鏉跨埣濡绢噣鏌涚€ｎ亜鈧湱鎹㈠☉銏犲耿婵☆垵顕х喊宥夋煟閻斿摜鎳曞┑鈥虫喘閸┾偓妞ゆ巻鍋撶紒鐘茬Ч瀹曟洟宕￠悙鈺傜亙闂佹寧娲栭崐褰掑磹閻戣姤鐓熼柟瀵稿€栭幋婵冩瀺?{(item.wareName)!''}</div>
                                        <div class="alarm-tit">闂傚倸鍊搁崐椋庣矆娓氣偓楠炲鏁嶉崟顓犵厯闂佺鎻梽鍕疾濠靛鐓忓┑鐐靛亾濞呮捇鏌℃担鍛婎棦闁哄矉缍佸顕€宕掑顑跨帛缂傚倷绶￠崑鍕矓瑜版帒钃熸繛鎴欏焺閺佸啴鏌ㄥ┑鍡橆棤妞わ负鍔戝娲箰鎼达絻鈧帡鏌涢悩宕囧⒌鐎?{(item.alarmTime)!''}</div>
                                    </div>
                                </li>
                            </#list>
                        </#if>
                    </ul>
                </div>
            </div>
        </div>
    </div>

    <div class="charts-grid">
        <div class="card-box chart-card">
            <div class="chart-title">闂傚倸鍊搁崐椋庣矆娓氣偓楠炲鏁嶉崟顓犵厯闂佺鎻梽鍕疾濠靛鐓忓┑鐐靛亾濞呮捇鏌℃担鍛婎棦闁哄矉缍佸顕€宕掑顒€顬嗛梻浣筋嚃閸剟宕曢悽绋跨畺婵°倕鎳忛弲鏌ュ箹鐎涙绠橀柣鈺侀叄濮?/div>
            <div class="chart-canvas" id="trend-chart"></div>
        </div>
        <div class="card-box chart-card">
            <div class="chart-title">闂傚倸鍊搁崐椋庣矆娓氣偓楠炲鏁嶉崟顓犵厯闂佺鎻梽鍕疾濠靛鐓忓┑鐐靛亾濞呮捇鏌℃担鍛婎棦闁哄矉缍佸顕€宕掑顑跨帛缂傚倷璁查崑鎾绘煕閳╁啰鈯曢柣鎾存礋閺屽秹鍩℃担鍛婃闂佺懓鍟垮Λ娆撳Φ閸曨垼鏁冩い鎺戝€婚弳銈夋⒑閸濆嫭婀扮紒瀣灱閻忓啴姊洪崨濠傚闁告捇浜堕弫鎰緞鐎ｎ剙骞?/div>
            <div class="chart-canvas" id="pie-chart"></div>
        </div>
        <div class="card-box chart-card">
            <div class="chart-title">闂傚倸鍊搁崐鐑芥嚄閸洏鈧焦绻濋崑鑺ョ洴瀹曠喖顢樺☉妯瑰濠殿喗顭囬崢褎鏅剁€电硶鍋撶憴鍕婵炲弶鐗犻敐鐐测攽鐎ｅ灚鏅ｉ梺缁樺姉閺佹悂鎮伴鈧缁樻媴閸涘﹥鍎撳銈忕細閸楀啿鐣疯ぐ鎺戦唶闁哄洨鍋熼弻鍫ユ⒑缂佹ê濮夐柛搴涘€濆畷鎴︽晲婢跺鍘撻悷婊勭矒瀹曟粓鎮㈤悡搴㈢€梺绋跨灱閸嬬偟鈧數濮撮…璺ㄦ崉閾忓湱浼囬梺鐟板暱濞差厼顫?/div>
            <div class="chart-canvas" id="ranking-chart"></div>
        </div>
    </div>
</div>

<script type="text/html" id="statics-item-tpl">
{{each datas }}
<div>{{ $value.name }}: <span id="statics_{{ $value.id }}">{{ $value.staticsFlagVal }}</span></div>
{{/each}}
</script>

<script type="text/html" id="alarm-item-tpl">
    <li class="alarm-item" onclick="openAlarmDetailById('{{ id }}');">
        <div class="alarm-box">
            <div class="alarm-img" id="alarm_{{ id }}">
                <img src="/report/stream?id={{ id }}" style="width: 100%; height: 100%; object-fit: contain;" params="{{ params }}" onload="handleImgLoad(this, '{{ id }}');" />
            </div>
            <div class="alarm-tit">闂傚倸鍊搁崐椋庣矆娓氣偓楠炲鏁嶉崟顓犵厯闂佺鎻梽鍕疾濠靛鐓忓┑鐐靛亾濞呮捇鏌℃担鍛婎棦闁哄矉缍佸顕€宕堕…鎴滃摋闂佽绻愮换鎺楀极婵犳艾绠栫憸鐗堝笒閻愬﹥銇勮箛鎾愁伀婵絻鍨藉娲焻閻愯尪瀚板褌鍗抽弻娑滅疀閺冩捁鈧法鈧鍣崑鍕敇婵傜妞藉ù锝堫潐閵嗗啰绱?algorithmName }}</div>
            <div class="alarm-tit">闂傚倸鍊搁崐椋庣矆娴ｉ潻鑰块梺顒€绉查埀顒€鍊圭粋鎺斺偓锝庝簽閿涙盯姊洪悷鏉库挃缂侇噮鍨跺畷鎰板醇閺囩喓鍘介梺瑙勫婢ф鈽夎闇夐柣娆忔噽閻ｇ敻鏌″畝鈧崰鏍х暦閿濆棗绶炲┑鐘插€归幊娆撴煟鎼淬値娼愭繛鍙夛耿楠炴垶鎷呴搹閫涚瑝濠电偞鍨崹鍦尵瀹ュ鐓冪憸婊堝礈濞戙垺鍤嶉柛妤冨剱濞尖晠鏌ら崫銉︽毄闁告瑥妫楅埞鎴︽倷閺夋垹浠搁梺鑽ゅ櫏閸ㄨ泛鐣峰┑瀣剨?cameraName }}</div>
            <div class="alarm-tit">闂傚倸鍊搁崐椋庣矆娓氣偓楠炲鏁撻悩鍐蹭罕闂佸搫娲㈤崹鍦不鐟欏嫨浜滈柟鏉跨埣濡绢噣鏌涚€ｎ亜鈧湱鎹㈠☉銏犲耿婵☆垵顕х喊宥夋煟閻斿摜鎳曞┑鈥虫喘閸┾偓妞ゆ巻鍋撶紒鐘茬Ч瀹曟洟宕￠悙鈺傜亙闂佹寧娲栭崐褰掑磹閻戣姤鐓熼柟瀵稿€栭幋婵冩瀺闁哄诞鈧弨鑺ャ亜閺冨倹娅曠紒鐘趁湁?wareName }}</div>
            <div class="alarm-tit">闂傚倸鍊搁崐椋庣矆娓氣偓楠炲鏁嶉崟顓犵厯闂佺鎻梽鍕疾濠靛鐓忓┑鐐靛亾濞呮捇鏌℃担鍛婎棦闁哄矉缍佸顕€宕掑顑跨帛缂傚倷绶￠崑鍕矓瑜版帒钃熸繛鎴欏焺閺佸啴鏌ㄥ┑鍡橆棤妞わ负鍔戝娲箰鎼达絻鈧帡鏌涢悩宕囧⒌鐎殿喖顭锋俊鎼佸Ψ閵忊槅娼旀繝鐢靛仜濡瑩鏁嬫繝娈垮枤濠€?alarmTime }}</div>
        </div>
    </li>
</script>

<script src="/static/component/layui/layui.js"></script>
<script src="/static/component/pear/pear.js"></script>
<script src="/static/js/flv.min.js"></script>
<script src="/static/js/jquery.3.6.1.min.js"></script>
<script src="/static/js/template-web.js"></script>
<script>
layui.use(['jquery', 'util', 'loading', 'popup', 'echarts'], function() {
    let $ = layui.jquery;
    let loading = layui.loading;
    let popup = layui.popup;
    let echarts = layui.echarts;

    var cols = 1;
    var ws = null;
    var wsTimer = null;
    var trendChart = null;
    var pieChart = null;
    var rankingChart = null;

    var playerMap = new Map();
    var cameraMap = new Map();
    var containerMap = new Map();
    var frameMap = new Map();
    var STORAGE_THEME_KEY = 'stream.dashboard.theme';
    var STORAGE_LANG_KEY = 'stream.dashboard.lang';
    var activeTheme = 'light';
    var activeLang = 'zh-CN';
    window.i18nMessages = {
        'zh-CN': {
            'global.theme': '[CN] Theme',
            'global.language': '[CN] Language',
            'theme.light': '[CN] Light',
            'theme.dark': '[CN] Dark',
            'theme.cyber': '[CN] Cyber Cockpit',
            'theme.apple': '[CN] Apple Minimal',
            'stream.preview': '[CN] Monitoring Dashboard',
            'stream.grid': '[CN] Grid',
            'stream.statConfig': '[CN] Statistics Config',
            'stream.clickSelect': '[CN] Click to select camera',
            'stream.selectCamera': '[CN] Select Camera',
            'overview.today': '[CN] Today Alerts',
            'overview.currentDate': '[CN] Date',
            'overview.onlineCameras': '[CN] Online Cameras',
            'overview.totalChannels': '[CN] Total: ',
            'overview.algorithms': '[CN] Algorithms',
            'overview.algorithmHint': '[CN] Available algorithm packages',
            'overview.models': '[CN] Models',
            'overview.modelHint': '[CN] Imported models',
            'scheduler.title': '[CN] Scheduler Feedback',
            'scheduler.pressure': '[CN] Concurrency Pressure',
            'scheduler.level': '[CN] Concurrency Level',
            'scheduler.stride': '[CN] Recommended Frame Stride',
            'scheduler.source': '[CN] Source',
            'scheduler.dispatch': '[CN] Suggested Min Dispatch (ms)',
            'scheduler.cooldown': '[CN] Max Effective Cooldown',
            'scheduler.telemetry': '[CN] Telemetry',
            'scheduler.statusOk': '[CN] OK',
            'scheduler.statusDegraded': '[CN] Degraded',
            'scheduler.error': '[CN] Telemetry Error',
            'scheduler.errorNone': '[CN] None',
            'alarm.latest': '[CN] Latest Alerts',
            'alarm.todayCount': '[CN] Today Alert Count',
            'alarm.type': '[CN] Alert Type',
            'alarm.camera': '[CN] Camera',
            'alarm.area': '[CN] Area',
            'alarm.time': '[CN] Alert Time',
            'alarm.detail': '[CN] Alert Detail',
            'alarm.noDetection': '[CN] No detections',
            'alarm.listTab': '[CN] Alert List',
            'chart.trend': '[CN] Alert Trend',
            'chart.pie': '[CN] Alert Category Ratio',
            'chart.ranking': '[CN] Camera Alert Ranking',
            'chart.alertCount': '[CN] Alert Count',
            'table.label': '[CN] Label',
            'table.confidence': '[CN] Confidence',
            'table.bbox': '[CN] Bounding Box',
            'msg.cameraAlreadySelected': '[CN] Camera already selected in another panel.',
            'msg.playUrlMissing': '[CN] No playable URL found.',
            'msg.mediaError': '[CN] Media decode failed.',
            'msg.networkError': '[CN] Network error.',
            'msg.playerError': '[CN] Player start failed.',
            'msg.operationFailed': '[CN] Operation failed'
        },
        'zh-TW': {
            'global.theme': '[TW] Theme',
            'global.language': '[TW] Language',
            'theme.light': '[TW] Light',
            'theme.dark': '[TW] Dark',
            'theme.cyber': '[TW] Cyber Cockpit',
            'theme.apple': '[TW] Apple Minimal',
            'stream.preview': '[TW] Monitoring Dashboard',
            'stream.grid': '[TW] Grid',
            'stream.statConfig': '[TW] Statistics Config',
            'stream.clickSelect': '[TW] Click to select camera',
            'stream.selectCamera': '[TW] Select Camera',
            'overview.today': '[TW] Today Alerts',
            'overview.currentDate': '[TW] Date',
            'overview.onlineCameras': '[TW] Online Cameras',
            'overview.totalChannels': '[TW] Total: ',
            'overview.algorithms': '[TW] Algorithms',
            'overview.algorithmHint': '[TW] Available algorithm packages',
            'overview.models': '[TW] Models',
            'overview.modelHint': '[TW] Imported models',
            'scheduler.title': '[TW] Scheduler Feedback',
            'scheduler.pressure': '[TW] Concurrency Pressure',
            'scheduler.level': '[TW] Concurrency Level',
            'scheduler.stride': '[TW] Recommended Frame Stride',
            'scheduler.source': '[TW] Source',
            'scheduler.dispatch': '[TW] Suggested Min Dispatch (ms)',
            'scheduler.cooldown': '[TW] Max Effective Cooldown',
            'scheduler.telemetry': '[TW] Telemetry',
            'scheduler.statusOk': '[TW] OK',
            'scheduler.statusDegraded': '[TW] Degraded',
            'scheduler.error': '[TW] Telemetry Error',
            'scheduler.errorNone': '[TW] None',
            'alarm.latest': '[TW] Latest Alerts',
            'alarm.todayCount': '[TW] Today Alert Count',
            'alarm.type': '[TW] Alert Type',
            'alarm.camera': '[TW] Camera',
            'alarm.area': '[TW] Area',
            'alarm.time': '[TW] Alert Time',
            'alarm.detail': '[TW] Alert Detail',
            'alarm.noDetection': '[TW] No detections',
            'alarm.listTab': '[TW] Alert List',
            'chart.trend': '[TW] Alert Trend',
            'chart.pie': '[TW] Alert Category Ratio',
            'chart.ranking': '[TW] Camera Alert Ranking',
            'chart.alertCount': '[TW] Alert Count',
            'table.label': '[TW] Label',
            'table.confidence': '[TW] Confidence',
            'table.bbox': '[TW] Bounding Box',
            'msg.cameraAlreadySelected': '[TW] Camera already selected in another panel.',
            'msg.playUrlMissing': '[TW] No playable URL found.',
            'msg.mediaError': '[TW] Media decode failed.',
            'msg.networkError': '[TW] Network error.',
            'msg.playerError': '[TW] Player start failed.',
            'msg.operationFailed': '[TW] Operation failed'
        },
        'en': {
            'global.theme': 'Theme',
            'global.language': 'Language',
            'theme.light': 'Light',
            'theme.dark': 'Dark',
            'theme.cyber': 'Cyber Cockpit',
            'theme.apple': 'Apple Minimal',
            'stream.preview': 'Monitoring Dashboard',
            'stream.grid': 'Grid',
            'stream.statConfig': 'Statistics Config',
            'stream.clickSelect': 'Click to select camera',
            'stream.selectCamera': 'Select Camera',
            'overview.today': 'Today Alerts',
            'overview.currentDate': 'Date',
            'overview.onlineCameras': 'Online Cameras',
            'overview.totalChannels': 'Total: ',
            'overview.algorithms': 'Algorithms',
            'overview.algorithmHint': 'Available algorithm packages',
            'overview.models': 'Models',
            'overview.modelHint': 'Imported models',
            'scheduler.title': 'Scheduler Feedback',
            'scheduler.pressure': 'Concurrency Pressure',
            'scheduler.level': 'Concurrency Level',
            'scheduler.stride': 'Recommended Frame Stride',
            'scheduler.source': 'Source',
            'scheduler.dispatch': 'Suggested Min Dispatch (ms)',
            'scheduler.cooldown': 'Max Effective Cooldown',
            'scheduler.telemetry': 'Telemetry',
            'scheduler.statusOk': 'OK',
            'scheduler.statusDegraded': 'Degraded',
            'scheduler.error': 'Telemetry Error',
            'scheduler.errorNone': 'None',
            'alarm.latest': 'Latest Alerts',
            'alarm.todayCount': 'Today Alert Count',
            'alarm.type': 'Alert Type',
            'alarm.camera': 'Camera',
            'alarm.area': 'Area',
            'alarm.time': 'Alert Time',
            'alarm.detail': 'Alert Detail',
            'alarm.noDetection': 'No detections',
            'alarm.listTab': 'Alert List',
            'chart.trend': 'Alert Trend',
            'chart.pie': 'Alert Category Ratio',
            'chart.ranking': 'Camera Alert Ranking',
            'chart.alertCount': 'Alert Count',
            'table.label': 'Label',
            'table.confidence': 'Confidence',
            'table.bbox': 'Bounding Box',
            'msg.cameraAlreadySelected': 'The camera is already being previewed in another panel.',
            'msg.playUrlMissing': 'No playable URL found. Please check stream settings.',
            'msg.mediaError': 'Media decode failed. Please check the stream format.',
            'msg.networkError': 'Network error. Please check device and network.',
            'msg.playerError': 'Failed to start player. Please retry.',
            'msg.operationFailed': 'Operation failed'
        }
    };

    window.t = function(key) {
        var langData = window.i18nMessages[activeLang] || window.i18nMessages['zh-CN'];
        return langData[key] || key;
    };

    window.resolveTelemetryStatusText = function(status) {
        return status === 'degraded' ? window.t('scheduler.statusDegraded') : window.t('scheduler.statusOk');
    };

    window.applyTheme = function(theme) {
        var nextTheme = ['light', 'dark', 'cyber', 'apple'].indexOf(theme) >= 0 ? theme : 'light';
        activeTheme = nextTheme;
        $('body.pear-container')
            .removeClass('theme-light theme-dark theme-cyber theme-apple')
            .addClass('theme-' + nextTheme);
        $('#theme-switch').val(nextTheme);
        localStorage.setItem(STORAGE_THEME_KEY, nextTheme);
    };

    window.relabelAlarmItems = function() {
        $('#alarm-ul-list .alarm-box').each(function() {
            var lines = $(this).find('.alarm-tit');
            if (lines.length < 4) {
                return;
            }
            var values = [
                window.stripFieldPrefix(lines.eq(0).text()),
                window.stripFieldPrefix(lines.eq(1).text()),
                window.stripFieldPrefix(lines.eq(2).text()),
                window.stripFieldPrefix(lines.eq(3).text())
            ];
            lines.eq(0).text(window.t('alarm.type') + ': ' + values[0]);
            lines.eq(1).text(window.t('alarm.camera') + ': ' + values[1]);
            lines.eq(2).text(window.t('alarm.area') + ': ' + values[2]);
            lines.eq(3).text(window.t('alarm.time') + ': ' + values[3]);
        });
    };

    window.applyLanguage = function(lang) {
        var nextLang = window.i18nMessages[lang] ? lang : 'zh-CN';
        activeLang = nextLang;
        $('#lang-switch').val(nextLang);
        localStorage.setItem(STORAGE_LANG_KEY, nextLang);
        var alarmLinkHtml = $('.video-alarm-counter .alarm_counter').prop('outerHTML') || '';

        var translatable = [
            ['.global-toolbar label[for=\"theme-switch\"]', window.t('global.theme')],
            ['.global-toolbar label[for=\"lang-switch\"]', window.t('global.language')],
            ['#theme-switch option[value=\"light\"]', window.t('theme.light')],
            ['#theme-switch option[value=\"dark\"]', window.t('theme.dark')],
            ['#theme-switch option[value=\"cyber\"]', window.t('theme.cyber')],
            ['#theme-switch option[value=\"apple\"]', window.t('theme.apple')],
            ['.overview-grid .overview-label:eq(0)', window.t('overview.today')],
            ['.overview-grid .overview-label:eq(1)', window.t('overview.onlineCameras')],
            ['.overview-grid .overview-label:eq(2)', window.t('overview.algorithms')],
            ['.overview-grid .overview-label:eq(3)', window.t('overview.models')],
            ['.overview-grid .overview-extra:eq(2)', window.t('overview.algorithmHint')],
            ['.overview-grid .overview-extra:eq(3)', window.t('overview.modelHint')],
            ['#scheduler-title', window.t('scheduler.title')],
            ['#scheduler-status-label', window.t('scheduler.telemetry')],
            ['#scheduler-pressure-label', window.t('scheduler.pressure')],
            ['#scheduler-stride-label', window.t('scheduler.stride')],
            ['#scheduler-dispatch-label', window.t('scheduler.dispatch')],
            ['.stream-nav-title', window.t('stream.preview')],
            ['.stream-btn-group [data-grid=\"1\"]', '1 ' + window.t('stream.grid')],
            ['.stream-btn-group [data-grid=\"4\"]', '4 ' + window.t('stream.grid')],
            ['.stream-btn-group [data-grid=\"9\"]', '9 ' + window.t('stream.grid')],
            ['.stream-btn-group [data-grid=\"16\"]', '16 ' + window.t('stream.grid')],
            ['.stream-header .layui-btn-normal', window.t('stream.statConfig')],
            ['.video-alarm-tit', window.t('alarm.latest')],
            ['.video-alarm-counter', window.t('alarm.todayCount') + ': ' + alarmLinkHtml],
            ['.charts-grid .chart-title:eq(0)', window.t('chart.trend')],
            ['.charts-grid .chart-title:eq(1)', window.t('chart.pie')],
            ['.charts-grid .chart-title:eq(2)', window.t('chart.ranking')]
        ];

        for (var i = 0; i < translatable.length; i++) {
            var selector = translatable[i][0];
            var text = translatable[i][1];
            var node = $(selector).first();
            if (node.length === 0) {
                continue;
            }
            if (selector === '.video-alarm-counter') {
                node.html(text);
                continue;
            }
            node.text(text);
        }
        var currentDate = $('#overview-date').text() || '-';
        var currentTotal = $('#overview-total-camera').text() || '0';
        var currentLevel = $('#scheduler-level-extra').attr('data-value') || '0';
        var currentSource = $('#scheduler-source-extra').attr('data-value') || 'scheduler_feedback';
        var currentCooldown = $('#scheduler-cooldown-extra').attr('data-value') || '0';
        var currentTelemetryStatus = $('#scheduler-status-badge').attr('data-status') || 'ok';
        var currentTelemetryError = $('#scheduler-error-extra').attr('data-value') || '';
        $('.overview-grid .overview-extra:eq(0)').html(window.t('overview.currentDate') + ' <span id=\"overview-date\">' + window.escapeHtml(currentDate) + '</span>');
        $('.overview-grid .overview-extra:eq(1)').html(window.t('overview.totalChannels') + '<span id=\"overview-total-camera\">' + window.escapeHtml(currentTotal) + '</span>');
        $('#scheduler-level-extra').text(window.t('scheduler.level') + ': ' + currentLevel);
        $('#scheduler-source-extra').text(window.t('scheduler.source') + ': ' + currentSource);
        $('#scheduler-cooldown-extra').text(window.t('scheduler.cooldown') + ': ' + currentCooldown + ' ms');
        $('#scheduler-error-extra')
            .attr('data-value', currentTelemetryError)
            .text(window.t('scheduler.error') + ': ' + (currentTelemetryError ? currentTelemetryError : window.t('scheduler.errorNone')));
        $('#scheduler-status-badge')
            .removeClass('scheduler-status-ok scheduler-status-degraded')
            .addClass(currentTelemetryStatus === 'degraded' ? 'scheduler-status-degraded' : 'scheduler-status-ok')
            .text(window.resolveTelemetryStatusText(currentTelemetryStatus));

        window.relabelAlarmItems();
        if (trendChart && pieChart && rankingChart) {
            window.refreshDashboardSummary();
        }
    };

    window.initSocket = function() {
        ws = new WebSocket('${wsUrl!''}/report/${uid!''}');
        ws.onopen = function() {
            wsTimer = setInterval(function() {
                if (ws && ws.readyState === WebSocket.OPEN) {
                    ws.send(JSON.stringify({'r': new Date().getTime()}));
                }
            }, 10000);
        };

        ws.onmessage = function(event) {
            var res = JSON.parse(event.data);
            if (res['type'] === 'REPORT') {
                window.paintCanvas(res);
            } else if (res['type'] === 'REPORT_SHOW') {
                window.addAlarm(res);
                window.addAlarmAlert(res);
            }
        };

        ws.onclose = function() {
            ws = null;
            clearInterval(wsTimer);
            wsTimer = null;
            setTimeout(function() {
                window.initSocket();
            }, 5000);
        };
    };

    window.paintCanvas = function(payload) {
        var cameraId = payload['cameraId'];
        if (!cameraMap.has(cameraId)) {
            return;
        }
        var containerId = cameraMap.get(cameraId);
        if (!containerId) {
            return;
        }

        var wrapper = $('#wrapper_' + containerId);
        var boxW = wrapper.width();
        var boxH = wrapper.height();
        var shownW = $('#' + containerId).width();
        var shownH = $('#' + containerId).height();
        var sourceW = document.getElementById(containerId).videoWidth;
        if (!sourceW) {
            return;
        }

        var extY = 0;
        var extX = 0;
        if ((boxW - shownW) > 5) {
            extX = parseInt((boxW - shownW) / 2, 10) + 1;
        } else {
            extY = parseInt((boxH - shownH) / 2, 10) + 1;
        }
        extX = extX + ((cols < 6) ? 5 : 2);
        extY = extY + ((cols < 6) ? 5 : 2);

        var ratio = (shownW / (sourceW * 2)).toFixed(2);
        wrapper.find('.frame').remove();

        var frames = [];
        try {
            frames = JSON.parse(payload['params'] || '[]');
        } catch (e) {
            frames = [];
        }

        for (var i = 0; i < frames.length; i++) {
            var p = frames[i]['position'];
            var tlx = parseInt(p[0] * ratio + extX, 10);
            var tly = parseInt(p[1] * ratio + extY, 10);
            var tlw = parseInt((p[2] - p[0]) * ratio, 10);
            var tlh = parseInt((p[3] - p[1]) * ratio, 10);
            wrapper.append('<div class="frame" style="display:block;left:' + tlx + 'px;top:' + tly + 'px;width:' + tlw + 'px;height:' + tlh + 'px;">' + (frames[i]['type'] || '') + '</div>');
        }

        setTimeout(function() {
            wrapper.find('.frame').remove();
        }, 300);
    };

    window.resolveAlarmLabels = function(json) {
        var labelsZh = json['alertLabelsZh'];
        if (Array.isArray(labelsZh) && labelsZh.length > 0) {
            return labelsZh;
        }
        var labels = json['alertLabels'];
        if (Array.isArray(labels) && labels.length > 0) {
            return labels;
        }
        return [];
    };

    window.resolveAlarmType = function(json) {
        var labels = window.resolveAlarmLabels(json);
        var count = parseInt(json['alertCount'], 10);
        var algorithmName = json['algorithmName'] || '';
        if (labels.length > 0) {
            algorithmName = labels.join(' / ');
        }
        if (!isNaN(count) && count > 1) {
            return algorithmName + ' x' + count;
        }
        return algorithmName;
    };

    window.buildAlarmTemplateData = function(json) {
        return {
            'cameraName': json['cameraName'],
            'algorithmName': window.resolveAlarmType(json),
            'wareName': json['wareName'],
            'alarmTime': json['alarmTime'],
            'params': json['params'],
            'id': json['id']
        };
    };

    window.escapeHtml = function(val) {
        return String(val || '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    };

    window.stripFieldPrefix = function(text) {
        var normalized = String(text || '');
        var idx = normalized.indexOf(':');
        if (idx < 0) {
            idx = normalized.indexOf('\uFF1A');
        }
        return idx >= 0 ? normalized.substring(idx + 1).trim() : normalized.trim();
    };

    window.parseAlarmParams = function(params) {
        try {
            var parsed = JSON.parse(params || '[]');
            return Array.isArray(parsed) ? parsed : [];
        } catch (e) {
            return [];
        }
    };

    window.openAlarmDetailById = function(alarmId) {
        var img = $('#alarm_' + alarmId + ' img').first();
        if (img.length === 0) {
            return;
        }
        var box = img.closest('.alarm-box');
        var lines = box.find('.alarm-tit');
        var detail = {
            id: String(alarmId || ''),
            imageUrl: img.attr('src') || '',
            params: img.attr('params') || '[]',
            algorithmName: window.stripFieldPrefix(lines.eq(0).text()),
            cameraName: window.stripFieldPrefix(lines.eq(1).text()),
            wareName: window.stripFieldPrefix(lines.eq(2).text()),
            alarmTime: window.stripFieldPrefix(lines.eq(3).text())
        };
        window.openAlarmDetailByData(detail);
    };

    window.openAlarmDetailByData = function(detail) {
        var detections = window.parseAlarmParams(detail.params);
        var rows = '';
        if (detections.length === 0) {
            rows = '<tr><td colspan="4">' + window.escapeHtml(window.t('alarm.noDetection')) + '</td></tr>';
        } else {
            for (var i = 0; i < detections.length; i++) {
                var item = detections[i] || {};
                var pos = item.position || [];
                var posText = Array.isArray(pos) && pos.length === 4 ? ('[' + pos.join(', ') + ']') : '-';
                rows += '<tr>'
                    + '<td>' + (i + 1) + '</td>'
                    + '<td>' + window.escapeHtml(item.type || '-') + '</td>'
                    + '<td>' + window.escapeHtml(item.confidence || '-') + '</td>'
                    + '<td>' + window.escapeHtml(posText) + '</td>'
                    + '</tr>';
            }
        }

        var html = ''
            + '<div class="alarm-detail-drawer">'
            + '  <div class="alarm-detail-head">' + window.escapeHtml(window.t('alarm.detail')) + ' #' + window.escapeHtml(detail.id) + '</div>'
            + '  <div class="alarm-detail-meta">'
            + '    <div>' + window.escapeHtml(window.t('alarm.type')) + ': ' + window.escapeHtml(detail.algorithmName) + '</div>'
            + '    <div>' + window.escapeHtml(window.t('alarm.camera')) + ': ' + window.escapeHtml(detail.cameraName) + '</div>'
            + '    <div>' + window.escapeHtml(window.t('alarm.area')) + ': ' + window.escapeHtml(detail.wareName) + '</div>'
            + '    <div>' + window.escapeHtml(window.t('alarm.time')) + ': ' + window.escapeHtml(detail.alarmTime) + '</div>'
            + '  </div>'
            + '  <div class="alarm-detail-img-wrap">'
            + '    <img class="alarm-detail-img" src="' + window.escapeHtml(detail.imageUrl) + '" alt="alarm" />'
            + '  </div>'
            + '  <table class="alarm-detect-table">'
            + '    <thead><tr><th>#</th><th>' + window.escapeHtml(window.t('table.label')) + '</th><th>' + window.escapeHtml(window.t('table.confidence')) + '</th><th>' + window.escapeHtml(window.t('table.bbox')) + '</th></tr></thead>'
            + '    <tbody>' + rows + '</tbody>'
            + '  </table>'
            + '</div>';

        layer.closeAll('page');
        layer.open({
            type: 1,
            title: window.t('alarm.detail'),
            shade: 0.2,
            area: ['560px', '100%'],
            offset: 'r',
            anim: -1,
            content: html
        });
    };

    window.addAlarm = function(json) {
        var algorithmId = json['algorithmId'];
        if ($('#statics_' + algorithmId).length > 0) {
            var oldVal = parseInt($('#statics_' + algorithmId).text(), 10);
            $('#statics_' + algorithmId).text(oldVal + 1);
        }

        var oldCounter = parseInt($('#alarm-counter').text(), 10);
        if (!isNaN(oldCounter)) {
            $('#alarm-counter').text(oldCounter + 1);
            $('#overview-today').text(oldCounter + 1);
        }

        var templateData = window.buildAlarmTemplateData(json);
        if ($('#alarm-ul-list li').length >= 3) {
            $('#alarm-ul-list li:last-child').remove();
        }
        var html = template('alarm-item-tpl', templateData);
        $('#alarm-ul-list').prepend(html);
        window.relabelAlarmItems();
    };

    window.addAlarmAlert = function(json) {
        var detail = window.buildAlarmTemplateData(json);
        window.openAlarmDetailByData(detail);
    };

    window.show = function(grid) {
        playerMap.forEach((player, containerId) => {
            window.closeVideo(containerId);
        });

        cols = [1, 4, 9, 16].indexOf(grid) >= 0 ? grid : 1;
        $('#btns [data-grid]').removeClass('stream-btn-active');
        $('#btns [data-grid="' + cols + '"]').addClass('stream-btn-active');
        $('#video-list').html('');

        var boxH = $('#video-list-wrapper').height();
        var boxW = $('#video-list-wrapper').width();
        if (boxH < 120) {
            boxH = 420;
            $('#video-list-wrapper').css('height', boxH + 'px');
        }
        var placeholder = window.t('stream.clickSelect');

        if (cols === 1) {
            var oneW = parseInt(boxW, 10) - 2;
            var oneH = parseInt(boxH, 10) - 2;
            var id = window.randomStr();
            $('#video-list').append('<div id="wrapper_' + id + '" style="width:' + oneW + 'px;height:' + oneH + 'px;" class="rel"><a href="javascript:void(0);" onclick="openForm(\'' + id + '\');"><div class="stream-panel lo' + id + '"><span id="cl_' + id + '">' + placeholder + '</span><video id="' + id + '" style="max-width:' + oneW + 'px;max-height:' + oneH + 'px;display:none;object-fit:contain;" muted></video></div></a><canvas id="cv_' + id + '" class="cv" height="0" width="0" ratio="0"></canvas><div class="stop-btn"><a href="javascript:void(0);" onclick="handleCloseByHand(\'' + id + '\');"><i class="layui-icon layui-icon-radio"></i></a></div></div>');
            $('.stream-panel').css('width', oneW + 'px').css('height', oneH + 'px');
            return;
        }

        var perRow = (cols === 4) ? 2 : (cols === 9 ? 3 : 4);
        var gap = 8;
        var cellW = parseInt((boxW - (perRow - 1) * gap) / perRow, 10);
        var cellH = parseInt((boxH - (perRow - 1) * gap) / perRow, 10);
        var clazz = (perRow === 2) ? 'layui-col-xs12 layui-col-sm6 layui-col-md6 rel' : (perRow === 3 ? 'layui-col-xs12 layui-col-sm6 layui-col-md4 rel' : 'layui-col-xs12 layui-col-sm6 layui-col-md3 rel');

        for (var i = 0; i < cols; i++) {
            var cid = window.randomStr();
            $('#video-list').append('<div id="wrapper_' + cid + '" class="' + clazz + '"><a href="javascript:void(0);" onclick="openForm(\'' + cid + '\');"><div class="stream-panel lo' + cid + '"><span id="cl_' + cid + '">' + placeholder + '</span><video id="' + cid + '" style="max-width:' + cellW + 'px;max-height:' + cellH + 'px;display:none;object-fit:contain;" muted></video></div></a><canvas id="cv_' + cid + '" class="cv" height="0" width="0" ratio="0"></canvas><div class="stop-btn"><a href="javascript:void(0);" onclick="handleCloseByHand(\'' + cid + '\');"><i class="layui-icon layui-icon-radio"></i></a></div><div id="frame_' + cid + '"></div></div>');
            $('.stream-panel').css('width', cellW + 'px').css('height', cellH + 'px');
        }
    };

    window.openForm = function(id) {
        layer.open({
            type: 2,
            title: window.t('stream.selectCamera'),
            shade: 0.1,
            area: ['50%', '50%'],
            content: '/stream/form?id=' + id
        });
    };

    window.resolvePlayUrl = function(data, cameraId) {
        if (typeof data === 'object' && data) {
            if (data.playUrl) {
                return data.playUrl;
            }
            if (data.videoPort) {
                return '${streamUrl!''}:' + data.videoPort + '/live/' + cameraId + '.flv';
            }
            return '';
        }
        if (typeof data === 'string' && data) {
            if (data.indexOf('rtsp://') === 0) {
                return '${streamUrl!''}/live?url=' + data;
            }
            return data;
        }
        return '';
    };

    window.selectRtsp = function(containerId, cameraId) {
        loading.block({ type: 2, elem: '.lo' + containerId, msg: '' });

        if (cameraMap.has(cameraId)) {
            popup.warning(window.t('msg.cameraAlreadySelected'));
            loading.blockRemove('.lo' + containerId, 1000);
            return;
        }

        $.post('/camera/selectPlay', { 'cameraId': cameraId }, function(res) {
            if (res.code === 0) {
                var playUrl = window.resolvePlayUrl(res.data, cameraId);
                if (!playUrl) {
                    loading.blockRemove('.lo' + containerId, 1000);
                    popup.failure(window.t('msg.playUrlMissing'));
                    return;
                }

                try {
                    window.handleClose(containerId);
                    var videoElement = document.getElementById(containerId);
                    var flvPlayer = flvjs.createPlayer({
                        url: playUrl,
                        type: 'flv',
                        enableWorker: true,
                        isLive: true,
                        hasAudio: false,
                        hasVideo: true,
                        enableStashBuffer: false,
                        stashInitialSize: 128
                    });
                    flvPlayer.attachMediaElement(videoElement);
                    flvPlayer.load();
                    flvPlayer.pause();

                    playerMap.set(containerId, flvPlayer);
                    cameraMap.set(cameraId, containerId);
                    containerMap.set(containerId, cameraId);

                    videoElement.addEventListener('canplay', function(e) {
                        var originalW = e.target.videoWidth;
                        var boxw = $('#wrapper_' + containerId).width();
                        var ratio = (boxw > originalW) ? (originalW / boxw).toFixed(2) : (boxw / originalW).toFixed(2);
                        $('#wrapper_' + containerId).attr('ratio', ratio);
                    });

                    flvPlayer.on(flvjs.Events.ERROR, function(err) {
                        loading.blockRemove('.lo' + containerId, 1000);
                        if (err === flvjs.ErrorTypes.MEDIA_ERROR) {
                            popup.failure(window.t('msg.mediaError'));
                        } else if (err === flvjs.ErrorTypes.NETWORK_ERROR) {
                            popup.failure(window.t('msg.networkError'));
                        } else {
                            popup.failure(window.t('msg.playerError'));
                        }
                        window.handleClose(containerId);
                    });

                    flvPlayer.on(flvjs.Events.METADATA_ARRIVED, function() {
                        $('#' + containerId).css('display', 'block');
                        loading.blockRemove('.lo' + containerId, 1000);
                        $('#cl_' + containerId).css('display', 'none');
                        flvPlayer.play();
                    });

                    flvPlayer.on('statistics_info', function(info) {
                        var currDecodedFrame = info.decodedFrames;
                        var cacheDecodedFrame = frameMap.get(containerId);
                        if (!cacheDecodedFrame) {
                            frameMap.set(containerId, currDecodedFrame + '_1');
                            return;
                        }
                        var lastDecodedFrameStr = cacheDecodedFrame.split('_')[0];
                        var lastDecodedFrameNum = cacheDecodedFrame.split('_')[1];
                        if (parseInt(lastDecodedFrameStr, 10) === currDecodedFrame) {
                            if (parseInt(lastDecodedFrameNum, 10) >= 20) {
                                window.handleClose(containerId);
                                window.selectRtsp(containerId, cameraId);
                            } else {
                                frameMap.set(containerId, currDecodedFrame + '_' + (parseInt(lastDecodedFrameNum, 10) + 1));
                            }
                        } else {
                            frameMap.set(containerId, currDecodedFrame + '_1');
                        }
                    });
                } catch (e) {
                    loading.blockRemove('.lo' + containerId, 1000);
                    popup.failure(window.t('msg.playerError'));
                }
            } else {
                loading.blockRemove('.lo' + containerId, 1000);
                popup.failure(res.msg || window.t('msg.operationFailed'));
            }
        });
    };

    window.closeVideo = function(containerId) {
        var player = playerMap.get(containerId);
        if (player) {
            try { player.pause(); } catch (e) {}
            try { player.detachMediaElement(); } catch (e) {}
            try { player.unload(); } catch (e) {}
            try { player.destroy(); } catch (e) {}
        }
        playerMap.delete(containerId);
        frameMap.delete(containerId);
        var cameraId = containerMap.get(containerId);
        if (cameraId) {
            cameraMap.delete(cameraId);
        }
        containerMap.delete(containerId);
    };

    window.handleClose = function(containerId) {
        var cv = document.getElementById('cv_' + containerId);
        cv.setAttribute('width', '0px');
        cv.setAttribute('height', '0px');
        $('#' + containerId).css('display', 'none');
        $('#cl_' + containerId).css('display', 'block');
        window.closeVideo(containerId);
    };

    window.handleCloseByHand = function(containerId) {
        var cv = document.getElementById('cv_' + containerId);
        cv.setAttribute('width', '0px');
        cv.setAttribute('height', '0px');
        $('#' + containerId).css('display', 'none');
        $('#cl_' + containerId).css('display', 'block');
        window.closeVideo(containerId);
    };

    window.randomStr = function() {
        var chars = 'abacdefghjklmnopqrstuvwxyzABCDEFGHJKLMNOPQRSTUVWXYZ0123456789';
        var size = chars.length;
        var str = '';
        for (var i = 0; i < size; i++) {
            str += chars.charAt(Math.floor(Math.random() * size));
        }
        return str;
    };

    window.handleStatics = function() {
        layer.open({
            type: 2,
            title: window.t('stream.statConfig'),
            shade: 0.1,
            area: ['70%', '70%'],
            content: '/stream/formConfig'
        });
    };

    window.handleStaticsTpl = function() {
        $.post('/stream/statics/algorithms', {}, function(res) {
            if (res.code === 0) {
                var html = template('statics-item-tpl', { 'datas': res.data });
                $('#statics-items').html(html);
            }
        });
    };

    window.handleImgLoad = function(obj, alarmBoxId) {
        var sWidth = $(obj).width();
        var nWidth = obj.naturalWidth;
        if (!nWidth) {
            return;
        }

        var ratio = (sWidth / nWidth).toFixed(2);
        var parsed = [];
        try {
            parsed = JSON.parse($(obj).attr('params') || '[]');
        } catch (e) {
            parsed = [];
        }

        for (var i = 0; i < parsed.length; i++) {
            var type = parsed[i]['type'];
            var confidence = parsed[i]['confidence'];
            var position = parsed[i]['position'];
            var startX = position[0];
            var startY = position[1];
            var width = position[2] - position[0];
            var height = position[3] - position[1];
            $('#alarm_' + alarmBoxId).append('<div class="alarm-mask" style="left:' + (startX * ratio) + 'px;top:' + (startY * ratio) + 'px;width:' + (width * ratio) + 'px;height:' + (height * ratio) + 'px;">' + type + '/' + confidence + '</div>');
        }
    };

    window.handleAlarmList = function() {
        parent.layui.admin.addTab('alarm_list', window.t('alarm.listTab'), '/report/list_card');
    };

    window.frameDelta = function() {
        for (var player of playerMap.values()) {
            if (player && player.buffered && player.buffered.length > 0) {
                var end = player.buffered.end(0);
                var delta = end - player.currentTime;
                if (delta >= 1) {
                    player.currentTime = player.buffered.end(0) - 0.3;
                }
            }
        }
    };

    window.buildDashboardCharts = function() {
        trendChart = echarts.init(document.getElementById('trend-chart'));
        pieChart = echarts.init(document.getElementById('pie-chart'));
        rankingChart = echarts.init(document.getElementById('ranking-chart'));
    };

    window.renderDashboardSummary = function(payload) {
        var overview = payload.overview || {};
        $('#overview-today').text(overview.todayAlerts || 0);
        $('#alarm-counter').text(overview.todayAlerts || 0);
        $('#overview-online').text(overview.onlineCameras || 0);
        $('#overview-total-camera').text(overview.totalCameras || 0);
        $('#overview-algorithm').text(overview.algorithmCount || 0);
        $('#overview-model').text(overview.modelCount || 0);
        $('#overview-date').text(payload.today || '-');
        var scheduler = payload.scheduler || {};
        var throttleHint = payload.throttle_hint || {};
        var pressureRaw = throttleHint.concurrency_pressure || scheduler.concurrency_pressure || 1;
        var pressure = Number(pressureRaw);
        if (!isFinite(pressure) || pressure <= 0) {
            pressure = 1;
        }
        var stride = parseInt(throttleHint.recommended_frame_stride || 1, 10);
        if (isNaN(stride) || stride <= 0) {
            stride = 1;
        }
        var level = parseInt(throttleHint.concurrency_level || scheduler.concurrency_level || 0, 10);
        if (isNaN(level) || level < 0) {
            level = 0;
        }
        var maxCooldown = parseInt(scheduler.max_effective_cooldown_ms || 0, 10);
        if (isNaN(maxCooldown) || maxCooldown < 0) {
            maxCooldown = 0;
        }
        var hintMinDispatch = parseInt(throttleHint.suggested_min_dispatch_ms || 0, 10);
        if (isNaN(hintMinDispatch) || hintMinDispatch <= 0) {
            hintMinDispatch = maxCooldown > 0 ? maxCooldown : (stride * 1000);
        }
        var source = String(throttleHint.strategy_source || 'scheduler_feedback');
        var telemetryStatus = String(payload.telemetry_status || 'ok').toLowerCase() === 'degraded' ? 'degraded' : 'ok';
        var telemetryError = String(payload.telemetry_error || '').trim();
        $('#scheduler-pressure').text(pressure.toFixed(2));
        $('#scheduler-stride').text(stride);
        $('#scheduler-dispatch').text(hintMinDispatch);
        $('#scheduler-level-extra').attr('data-value', String(level)).text(window.t('scheduler.level') + ': ' + level);
        $('#scheduler-source-extra').attr('data-value', source).text(window.t('scheduler.source') + ': ' + source);
        $('#scheduler-cooldown-extra').attr('data-value', String(maxCooldown)).text(window.t('scheduler.cooldown') + ': ' + maxCooldown + ' ms');
        $('#scheduler-error-extra')
            .attr('data-value', telemetryError)
            .text(window.t('scheduler.error') + ': ' + (telemetryError ? telemetryError : window.t('scheduler.errorNone')));
        $('#scheduler-status-badge')
            .attr('data-status', telemetryStatus)
            .attr('title', telemetryError)
            .removeClass('scheduler-status-ok scheduler-status-degraded')
            .addClass(telemetryStatus === 'degraded' ? 'scheduler-status-degraded' : 'scheduler-status-ok')
            .text(window.resolveTelemetryStatusText(telemetryStatus));

        var trend = payload.trend || {};
        trendChart.setOption({
            tooltip: { trigger: 'axis' },
            grid: { left: 35, right: 16, top: 30, bottom: 25 },
            xAxis: { type: 'category', data: trend.labels || [] },
            yAxis: { type: 'value' },
            series: [{
                name: window.t('chart.alertCount'),
                type: 'line',
                smooth: true,
                data: trend.values || [],
                areaStyle: { opacity: 0.15 },
                lineStyle: { width: 2, color: '#2b6cf6' },
                itemStyle: { color: '#2b6cf6' }
            }]
        });

        pieChart.setOption({
            tooltip: { trigger: 'item' },
            legend: { bottom: 0, type: 'scroll' },
            series: [{
                type: 'pie',
                radius: ['30%', '65%'],
                center: ['50%', '45%'],
                data: payload.pie || []
            }]
        });

        var ranking = payload.ranking || {};
        rankingChart.setOption({
            tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
            grid: { left: 35, right: 16, top: 30, bottom: 45 },
            xAxis: {
                type: 'category',
                data: ranking.labels || [],
                axisLabel: { interval: 0, rotate: 20 }
            },
            yAxis: { type: 'value' },
            series: [{
                type: 'bar',
                data: ranking.values || [],
                itemStyle: { color: '#16a34a' }
            }]
        });
    };

    window.refreshDashboardSummary = function() {
        $.post('/stream/dashboard/summary', {}, function(res) {
            if (res.code === 0) {
                window.renderDashboardSummary(res.data || {});
            }
        });
    };

    $(window).on('resize', function() {
        if (trendChart) { trendChart.resize(); }
        if (pieChart) { pieChart.resize(); }
        if (rankingChart) { rankingChart.resize(); }
    });

    $(document).ready(function() {
        var savedTheme = localStorage.getItem(STORAGE_THEME_KEY) || 'light';
        var savedLang = localStorage.getItem(STORAGE_LANG_KEY) || 'zh-CN';
        window.applyTheme(savedTheme);
        window.applyLanguage(savedLang);
        $('#theme-switch').on('change', function() {
            window.applyTheme($(this).val());
        });
        $('#lang-switch').on('change', function() {
            window.applyLanguage($(this).val());
        });

        window.show(cols);
        window.handleStaticsTpl();
        window.initSocket();
        window.buildDashboardCharts();
        window.refreshDashboardSummary();
        window.relabelAlarmItems();

        setInterval(function() {
            window.refreshDashboardSummary();
        }, 60000);

        setInterval(function() {
            window.frameDelta();
        }, 2000);
    });
});
</script>
</body>
</html>
