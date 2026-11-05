# 原生钱包锁屏消息顺序修复

日期：2026-10-14；基于本地 e0e1fe3。用户要求国行尽可能对齐国际版的呼出过程和效果。本次完成锁屏衔接修复；实机对照仍是验收条件，不以单元测试宣称完全一致。

## 直接依据

已读取两台手机当前的 services.jar、oplus-services.jar 和 SystemUI。电源识别、StatusBar 钱包通知、QuickAccessWalletController 回调及锁屏 reset 的关键判断一致。两台的 launchWalletOptionOnPowerDoubleTap 与 launchWalletViaSysuiCallbacks 均为 true；通过欧加框架自身查询，power_button_redefine 在国际版为 true、国行为 false。普通 pm has-feature 对这项内部能力在两台均返回 false，不能据此判定欧加框架的运行值。

国行连续日志捕获 29 次双击。典型失败为 15:26:36.006 钱包请求解除锁屏、36.029 原生 reset(true) 发送 notifyDismissCancelled、36.365 钱包通过 finishAndRemoveTask 自行关闭。源码中原生 reset 会取消已显示认证界面的请求，Wallet 的取消回调会收起页面。因此仅补电源资格没有解决请求先于锁屏覆盖状态更新的竞态。

钱包主 APK 在两台字节相同，国际版包为系统应用、国行为普通应用，GMS 版本不同；尚未证明这些差异直接造成重复认证，不据此系统化钱包、降级 GMS 或修改认证结果。

## 最小修改

继续使用已有原生电源识别与钱包回调。在 SystemUI 的实际 KeyguardViewMediator Handler 收到原生 DismissMessage 时，仅限以下条件才短暂保留消息：模块钱包开启，真实 callback 非空，锁屏正在显示且未被覆盖，当前用户顶部任务是 Google Wallet 的 QUICKDRAW。

使用 Message.obtain 保存原消息。原生 StatusBarKeyguardViewManager.reset 完成且覆盖状态已生效后，将副本交回同一个原生 Handler。原始 callback、参数、数据和目标均保留。没有生成成功/取消结果，没有跳过系统或 Google 的认证判断，没有 Hook 钱包应用。

队列限 8 个请求；没有状态事件时 500ms 兜底交回原流程，正常路径不固定等待。单次身份标记避免副本再次排队；旧计时器不能释放新批次。开关关闭或用户切换时，已接受的消息仍交给原生处理器决定。调度失败、查询异常、结构不匹配保留原流程；发送失败记录日志并清理副本，不伪造认证结果。普通 Keyguard 消息只做类型检查，不读取偏好或查询任务。

钱包作用域增加系统界面，保留 system；不增加 Google Wallet/GMS 应用作用域。新增组采用原子安装，任一步失败则回滚。本次未改首页/UI 文案、附属模块 payload 或版本号。

## 回归与审查

- 独立测试先用立即转发的旧行为 fixture 复现 RED，再在实际队列上 GREEN；立即转发变异被拒绝。
- 实际 Kotlin Hook 测试先证明原处理器早于 reset 会被取消，再验证完整消息在 reset 后仅处理一次。覆盖真实成功和取消、其他包/动作/用户、关闭功能、已覆盖/未锁定、无 callback、消息数据、500ms 兜底、排程/发送失败、查询和反射异常、用户切换与 reset 失败。
- 完整应用回归、Release 构建与 Lint 通过。Lint 为 0 错误、27 个既有警告；保留 getRunningTasks 的编译弃用提示，此处运行于具有 REAL_GET_TASKS 权限的 SystemUI，用于识别当前实际任务。
- 构建中发现测试 Bundle stub 丢失原有整数接口，已恢复；未删除或弱化原回归。
- 独立测试作者审查和主代理审查未发现正常主线程路径中请求重复、认证结果伪造或无界队列。实机系统时序、认证后页面是否保持、近期认证是否免重复，仍需用户对照。

## 测试包

outputs/Coogle-wallet-native-ordering-test.apk：1.3.3 / 133，2,834,750 字节；SHA-256 b9a064054a7562b9a4e0eaf11527f213e5573ee62994dfd93fc64afb73e68fb2。

签名与此前测试包一致。内嵌 assets/coogle-companion.zip 与上个测试包逐字节相同，SHA-256 548e5c2b69f7a164c08c425d8d3dbf7f512ca96a0726a316239f5bbbab419c5a。

已安装国行测试包。用户勾选系统界面作用域并手动重启后，确认认证后钱包保持显示，但仍报告每次需要指纹，近期认证行为尚未与国际版对齐。

重启后的 LSPosed 日志确认 SystemUI 新 Hook 已安装。16:10:37.898 / 37.913、16:10:44.292 / 44.302 等多组记录证明原请求在原生 reset 后交回，正常路径只等待约 7–16ms。这与用户页面保持的结果一致，不能据此宣称支付验证或整个链路已完全等价。

继续只读分析同一 Wallet APK：PaymentFocusedViewModel 的认证状态由 Google TapAndPay 服务事件提供；需要认证时，锁定设备分支调用 requestDismissKeyguard，未锁定分支调用生物识别并报告解锁。界面是否显示“身份认证”文字不能单独证明支付资格。国行 mIsSecureNfcEnabled=false，与此前国际版相同，排除这项开关的直接差异。当前仍需采集一次真实成功后 5 秒内再次双击的完整日志，判断重复认证来源。

未推送 GitHub、修改首页、更新已发布 Release 或代用户重启设备。

## 后续认证定位与当前约束

用户确认两台同一账号、默认付款卡相同，但国际版 2 张付款卡、国行 8 张。均为多卡，不能归因于单卡自动选择。设备读取确认国际版 SDK 36 / Android 16、国行 SDK 37 / Android 17；Wallet 主 APK 仍为同一 SHA-256，GMS 版本不同。

已核对两版 GMS 的近期认证实现：读取 BiometricManager.getLastAuthenticationTime(32783)，按原有有效期判断；不支持时使用原有记录及 Android Keystore 校验。两台 Wallet 角色均为 com.google.android.gms，默认 NFC 服务均为 TpHceService。系统 KeyguardManager/TrustManager 锁定查询未发现 Wallet 包专属豁免。

临时只读诊断于 16:51:05.298 记录：Google 返回 recent=true，lastAuthAgeMs=3606，locked=true，cards=8，selected=7，suppliedCard=false，valuables=0，requestAuth=false；同一原生回调结束后 requestAuth=true。16:51:12.946 / 12.947 再次复现。新版 Wallet 原有 xht 回调在缺少当前预览 CardInfo/ValuableInfo 且设备锁定时，即使近期认证有效，也设置界面解锁请求。该行为不同于“系统没有记录指纹”，也不能归因于 8 张卡列表尚未加载。

国际版本地持续采集确认 10:02:05.523 指纹成功、10:02:06.282 原生成功回调，10:02:09.864 再次启动 QUICKDRAW 后没有新的解锁请求；结果仍 showing=true、occluded=true、deviceLocked=1。日志中的 acek 关闭路径属于原有 acbs/acfc 界面；国行记录的 acax/xir/xht 属于另一界面实现。两套代码同在当前 APK 中。此前 TapAndPay 的 IDLE/AUTH_SUCCESS 警告来自 PollingFrameHandler，无付款设备场景下不能据此认定认证缓存失效。

用户随后明确要求保留国行新版界面，不继续纠结界面差别。已撤回选择原有界面的试验代码、DexKit 查询、新增 Wallet 作用域和相关测试修改。该试验未安装到手机、未提交。临时只读诊断源码已移除，国行已重新安装 Coogle-wallet-native-ordering-test.apk，并关闭诊断钱包进程；当前仍为 system + SystemUI 作用域，不 Hook Wallet 认证代码。

回退后 testHookLifecycle、assembleRelease、lintRelease 全部通过，记录在 build/wallet-new-ui-preserved-regression.log。保留原生启动与锁屏消息顺序修复；重复认证尚未修复，不宣称与国际版所有行为完全相同。继续工作的边界是保留新版界面和真实锁定/认证结果，不以伪造解锁状态或认证成功取得表面一致。

## 国际版近期认证与到期的完整对照

2026-10-14 后续仅通过 ADB 采集国际版，未安装诊断 APK 或修改认证设置。以下时间为设备日志时间，不代表北京时间。原始记录保存在忽略目录 build/wallet-native-input/intl-second-current。

- events.txt：第一次页面 task 4099 在 14:06:27.851 销毁，第二次 task 4100 在 14:06:38.532 新建。当前 dumpsys 显示 showing=true、occluded=true、deviceLocked=1，钱包页面中的 acbs 已直接确认。不能以复用同一个 Activity 解释这次免认证；进程内数据仍可能复用。
- full-round-auth-required.txt：14:09:24.606 指纹成功，14:09:27.275 和 14:09:44.657 分别新建 task 4101、4102；两次订阅 AuthStatusListenersManager，没有新的钱包解锁请求。
- 14:12:24.610 Google 服务发出 AuthStatus updated，14:12:24.625 原有 task 4102 的钱包主动请求 dismiss Keyguard，14:12:24.627 系统登记认证 callback。距本轮真实指纹成功约 180 秒，符合已读取的 Google 原生认证到期计时器实现；不是新一次双击才产生的请求。日志的 Authed 布尔值被 Google 脱敏，不能冒称直接读到了 false。

同一 Wallet APK 的源码进一步确认：qar 为原有 acfo 和新版 xir 提供同类认证仓库；qap 通过原生 amne.q() 查询认证状态。rwn 保存状态，qag 和 axcg 按认证值比较相等，brzb 对相同状态不再次发出更新。认证仓库经过账号键的 axie 缓存工厂获取，存在进程内复用的代码路径；不等于复用同一个页面。

原有 acfo 的初始 foregroundState 为 acbl.a；acfh 只有在 (!recent || deviceLocked) 且 foregroundState == acbl.b 时请求解锁。QUICKDRAW 随后由页面的生命周期收集器通过 acec / acfo.n() 切换为 acbl.b。新版 xir 初始 CardInfo 为空、ValuableInfo 为空，xht 对 (!recent || deviceLocked) 且两者为空立即设置请求。两者差异不是简单的原有实现只判断 !recent，也不能通过向原生启动 Intent 填入卡片事件数据来宣称同等安全。

因此，一个与日志一致的候选解释是：国际版新页面在初始状态收到已有效的认证结果时未触发解锁，随后相同结果不重复通知；到期变更在页面已进入 QUICKDRAW 状态时触发原生解锁。上述各个代码条件及实机到期顺序已核对，但国际版 acfh 实际回调时的 foregroundState 和私有回调顺序尚未直接采集，候选解释仍不能标为实机证实的根因。APK 未声明 debuggable/profileable；当前普通 ADB 活动转储只显示原生 Fragment 和生命周期，不暴露该 ViewModel 私有字段。重复相同双击和 logcat 采集不能自动补齐这个缺口。

本轮仅更新研究记录。暂不增加 Wallet 作用域、不切换界面、不修改认证判断，也不把此前基于 recent=true 跳过初始请求的适配建议称为原生等价实现。生产代码、测试 APK、附属模块、版本号和已发布产物均保持原样，未推送 GitHub。

## Root 读取与独立只读诊断准备

国际版 ADB 已取得 uid=0。通过 IBiometricService 读取到真实 lastAuthenticationTime（32783），但 `am profile start` 仍被系统以钱包未声明 debuggable/profileable 拒绝，Root 没有自动解除该限制。未改包标记或认证设置。

国际版 LSPosed 数据库只读核对：此前 Google 钱包没有作用域；GMS 仅有已禁用模块。system/SystemUI 仍存在其他启用模块，因此参考设备不能宣称完全无模块的原厂状态。

按用户“继续”授权，构建独立 `io.github.zopulus.coogle.walletdiagnostic`，标签“Coogle 钱包只读诊断”，只声明 Google 钱包作用域，没有 system/SystemUI 回调、功能设置或安装器。正式 Coogle 源码及 APK 保持原样。诊断源码和工程均在忽略目录 build/wallet-native-input/intl-readonly-module，不进入发布构建。

针对已确认的 Wallet 26.40.996156910，只记录 acfh/xht 原生认证回调前后、acfo.n 原生 QUICKDRAW 切换前后和 acfo 构造完成后的状态。记录真实 recent 值、locked、对应分支的 eligible、requestAuth、单调时钟、进程内模型身份和线程编号，不记录账户、卡号、卡片对象或 Intent extras；不写入参数、返回值、认证状态或字段。最多 200 条，进程启动后 10 分钟停止记录。Hook 与日志会增加少量时序开销，研究结论需要结合未注入时的原生日志判断。

assembleRelease、lintRelease 成功（0 errors、4 warnings；targetSdk、Xposed 服务导出、备份规则及临时模块图标检查），APK 50,947 字节，SHA-256 dba09e6918022bb8ad4f34694ad83b349d5cf0e8ac5a3bcc60be56a3d7bfcccd。已安装国际版，用户确认仅启用钱包作用域；已停止钱包旧进程，等待原生双击启动后的实际加载及回调记录。设备本地日志采集限时 240 秒。实机调用顺序仍待结果，不能提前宣称根因证实。测试结束应卸载独立诊断模块并关闭钱包进程，使其下次启动恢复未注入状态。

## 独立诊断实机结果：原有分支的回调顺序已确认

用户完成三次原生双击，报告“前两次指纹，第三次显示”。对应诊断文件 intl-second-current/diagnostic-order.txt 与 diagnostic-phone.log（设备时间）：

- 第一次 14:35:49.136 初始 recent=null、locked=true、eligible=false；49.161 QUICKDRAW 切换 eligible=true；49.259 收到 recent=false，原回调将 requestAuth=false 改为 true。14:35:54.992 系统 notifyDismissSucceeded。
- 第二次 14:36:05.548 新模型先收到旧 recent=false、eligible=false，未触发解锁；05.565 QUICKDRAW 切换 eligible=true；05.598 收到新 recent=true，此时仍 locked=true，原回调把 requestAuth 改为 true。14:36:09.450 系统 notifyDismissSucceeded。证明原有分支也会在有效认证结果较晚更新时重新要求指纹，不能简单概括成“recent=true 就不认证”。
- 第三次 14:36:17.969 新模型在构造完成之前收到缓存 recent=true，locked=true、eligible=false，原回调结束 requestAuth=false；17.986 QUICKDRAW 切换 eligible=true 后 requestAuth 仍 false，没有新的认证回调。用户确认直接显示。不是复用同一个 ViewModel 或 Activity，三个模型身份不同。

因此，之前候选的“初始前台状态门控 + 认证状态缓存/相等值不重发”路径已在本轮第三次呼出直接采集，并与未注入时原生近期免认证及约 180 秒到期后请求解锁的日志一致。只读 Hook 仍有少量时序影响，不能宣称所有启动时序下必然跳过认证。

这些判断位于钱包应用自身的 acfo/acfh，而非单纯的电源键策略或 SystemUI 认证缓存。国行新版 xir/xht 不具有相同初始 foregroundState 门控；只继续调整系统启动 Intent 不会自动获得这段行为。保留新版界面时若移植门控，需要钱包作用域及独立的生命周期适配，并保留真实 Google 认证变更、到期、卡片/CVM 明确认证请求；不能伪造近期认证结果或仅以最近指纹时间覆盖原有判断。当前尚未实现认证行为适配。

日志已保存后卸载 io.github.zopulus.coogle.walletdiagnostic，并关闭钱包进程清除已注入代码，下次启动恢复原有运行状态。正式 Coogle、附属模块与已发布产物均未改动，未重启设备或推送 GitHub。

## 国行新版初始预览适配与验证

用户授权保留新版界面并加入 Google 钱包作用域后，新增 WalletPreviewHooks。只在同线程、同一 PaymentFocusedViewModel 构造期间、当前任务为不携带付款事件的原生 QUICKDRAW 预览时，针对 Google 返回 recent=true 且锁定、无 CardInfo/ValuableInfo、尚未请求认证的原始 xht 回调，返回其正常 coroutine Unit，避免此次额外界面解锁请求。不修改认证状态、认证时间、锁定状态、付款资格或已经存在的请求。构造结束或异常后清理窗口；后续认证回调、到期以及明确付款/CVM 请求保留原始处理。

实现比国际版完整 foregroundState 窗口更窄：只覆盖构造阶段，且不跳过 false/未知认证值。不能称为逐行移植或所有启动时序完全等价。保留国行新版界面，不保证首次指纹之后下一次必然免认证。

绑定以已核对 Wallet base APK SHA-256 为边界；不匹配或查询失败时保留原始认证。APK 更新后双击入口仍存在，免重复认证适配停止，需重新审查新版。校验在钱包进程安装 Hook 时顺序读取约 43 MB APK，有一次启动开销，不在每次认证回调中扫描 APK。运行时只有构造期间任务查询和少量字段读取，无轮询、永久模型缓存或付款信息日志。未引入 DexKit 对认证代码的自动推断。

作用域已加入 FeatureScopes 与静态清单，沿用钱包开关每次开启时的一次申请流程；功能关闭后回调保留原处理。没有 UI 文案、附属模块或版本号变动。正式源码没有 WalletNativeDiagnostics 或独立诊断模块入口。

验证记录：
- 新增 WalletPreviewHookTest，在真实生产 Hook 拦截器上测试温缓存预览、冷/未知状态、初始化后的到期和有效值更新、明确 CVM、非预览/携带付款事件、查询失败、已有请求、关闭开关、跨模型/跨线程以及异常清理。
- 首次红测试因缺少 Hook 类失败；实现后通过。临时移除 recent=true 检查后测试以“Cold/loading callback was swallowed”失败；恢复原文件后完整 testHookLifecycle 再次通过。
- testHookLifecycle、assembleRelease、lintRelease 成功，Lint 0 errors、27 warnings。日志 build/wallet-preview-regression.log、build/wallet-preview-mutation.log、build/wallet-preview-restored-tests.log。
- 测试 APK outputs/Coogle-wallet-preview-gate-test.apk，2,834,762 字节，SHA-256 f1f8a4bbcff940c2cb0ffb6d0ae8901e8d209cdd7768dff53a19385b4e164999，已安装国行；用户确认勾选钱包作用域。
- 用户实测五次，前两次指纹、后三次直接显示。设备日志 21:53:03.620 确认安装；21:53:14.798、19.588、37.342 确认保留初始预览。Trust 转储 deviceLocked=1，未伪造系统解锁。第二次仍有新的 Google AuthStatus 通知与原生 dismiss 请求，符合国际版已采集的相同类型时序；该日志布尔值被脱敏，不能宣称直接读到第二次的具体认证值。
- 已保存原始记录于忽略目录 build/wallet-native-input/cn-preview-gate。没有收款终端验证，不能据此宣称实际支付安全性已完成等价验证；国行本轮认证到期后行为尚无受控实机对照，自动化测试已覆盖保留原回调。

本次代码审查未发现扩大认证拦截或遗留诊断路径。修改仍在本地工作区，未推送 GitHub、改首页、更新 Release 或重启设备。

## DexKit 候选定位与状态卡片更新（历史实现）

2026-11-05 精简：下述候选定位只用于诊断，已从正式启动路径连同专用扫描类和缓存分支移除。钱包完整 APK 校验与已核对的认证条件继续保留；未知 APK 不安装认证 Hook，旧候选缓存也不会启用已移除的定位。

新增 WalletPreviewCandidates，仅定位待审查的模型 getter 与认证回调；候选结果不授权安装认证 Hook。未知 APK 即使找到候选，也保留原始认证流程。当前已核对 APK 的适配仍由完整 SHA-256 校验独立授权，扫描错误不会导致它失效。

初版规则误要求 UI 状态只含一个 CardInfo，实际 xhp 同时包含 cardInfo 与 standaloneCardInfo 两个字段。实机记录曾返回 none or ambiguous；按真实双字段结构修改测试后，原实现以 Renamed relationship lost 失败。已修正为两个 CardInfo、两个 List，并将候选缓存键改为 wallet.preview-candidates-v2，移除旧 v1 键，避免不变 APK 继续复用旧空结果。测试覆盖原空缓存失效、有效缓存不加载扫描器、APK 更新重查，以及未知 APK 不能凭候选开启认证 Hook。v2 扫描结果仍待开启钱包后的实机确认；当前钱包开关按用户操作保持关闭。

安装入口移至激活状态卡片弹窗，原检测按钮改为安装附属模块，再进入原安装确认弹窗；左上角安装图标、onInstall 参数及 force 检测分支已移除。点击状态卡片可按需检查一次，以保留首次使用 APK、但附属模块已从 KSU 安装的状态确认路径。每次前台期间仍由原标志去重，仅打开未知状态的主页不会因此新增 Root 请求。状态弹窗加入附属模块介绍；Hey Google、系统导航方式、钱包与隐藏图标说明采用用户确认的文案。实时翻译安装提示仅在确认未安装时显示。

## 钱包关闭时的设置回读修复

国行保存的原手势为 enabled=0、action=null，关闭时需执行 settings delete 恢复动作未设置状态。Android SettingsService 的 DELETE 分支输出 Deleted N rows，原 writeCommand 把该成功提示与末尾两行 get 输出一起交给严格解析器，误判读取失败并回滚。

修复仅对 put/delete 标准输出加重定向，不隐藏 stderr、不忽略退出状态、不放宽最终两项回读校验；错误仍触发原有恢复逻辑。测试模拟删除 0/1 行的真实输出、两项原设置都不存在，以及删除错误仍保留 stderr 和非零退出结果。红测试失败于 Absent native target was not restored，修复后完整 testHookLifecycle、assembleRelease、lintRelease 成功，Lint 0 errors、27 warnings。日志 build/wallet-disable-delete-red.log 与 build/card-copy-wallet-disable-final.log。

测试包 outputs/Coogle-1.3.3-local-test.apk，2,834,762 字节，SHA-256 c240a73a10c539263aacb4c4f98da863dec5749b538892ca532296785dc5811c，已通过 ADB 安装国行。用户确认已关闭钱包；只读回查 enabled=0、action=null，wallet_native_gesture 恢复备份为空，截图也显示钱包开关关闭及新文案。附属模块、首页、发布版本未更改，无 GitHub 推送或设备重启。


## 附属模块安装流程与错误提示收尾（2026-10-14）

按用户确认方案，状态弹窗的安装按钮直接调用安装流程，删除第二次安装确认的 DialogKind.INSTALL、文案及分支。删除不再使用的自定义模块图标，完成弹窗使用 Material Icons.Outlined.Extension，与 KernelSU Material 底栏模块未选择图标一致。安装完成不再报告 Hey Google 开启或关闭；需要时提示在 Google 设置中开启 Voice Match。安装期间卡片保留上次状态，不显示正在安装。

检测失败直接显示中文原因，不拼接旧安装状态，也不在状态弹窗重复原因。安装失败只显示操作错误，不冒充检测失败。RootShell 增加同一次 su 调用内的 uid=0 确认标记，并在返回命令输出前剥离；授权未取得、无可用 su、超时、授权后命令失败分别分类。只有未取得授权显示“需要 Root 授权 / 请在 KernelSU 中授予 Root 权限后重试。”平台异常不直接透传给界面。保留输出限制、超时及失败处理，输出读取错误不再静默当作成功。安装入口增加重复操作保护。

审查覆盖本地 UI、Root 错误、附属模块状态同步及本次未提交的钱包/DexKit改动；未发现需要继续删除的明确废弃生产分支。保留必要的版本保护、失败恢复和候选缓存失效处理。RootShellTest 覆盖授权拒绝、已授权命令失败、超时销毁、读取失败及手势两行输出协议；在独立副本中故意把执行失败改为授权失败，测试以 Wrong failure category 失败，生产源码未参与变异。状态测试覆盖检测错误不改变内部已知状态、安装时不替换卡片文案。

完整 testHookLifecycle、assembleRelease、lintRelease 成功（1m19s），Lint 0 errors、27 warnings，无新增警告。日志 build/companion-ui-errors-review.log；测试包 outputs/Coogle-1.3.3-local-test.apk，2,834,762 字节，SHA-256 d1aa5f72b1e66602700a8ac8dfe2b2b6298def08405e350b7584025cb33cefd6。本次 ADB 仅连接国际版参考机，没有安装本包或触发附属模块安装；新提示及图标仍待国行实机确认。附属模块、首页、已发布 Release 不变，未推送、未重启。


## 未授权安装后的缓存状态回退修复

用户发现未获 Root 权限时尝试安装附属模块，卡片从检测失败变回缓存的安装状态。原因是 installCompanion 开始时清空 companionError，失败后只显示错误弹窗。改为通过 beginCompanionInstall 保留原错误；重新检测期间也不提前清除错误，只有成功返回状态才通过 confirmCompanion 清除。安装因授权缺失或无 su 失败时将对应中文原因同步到卡片，普通安装错误不冒充检测错误。

新增状态回归覆盖全部 CompanionState，验证安装重试保留失败摘要及内部上次状态，重新检测未返回前也保留失败原因。testHookLifecycle、assembleRelease、lintRelease 均通过（1m18s，0 errors、27 warnings）；日志 build/companion-install-status-final.log。用户撤回钱包免 Root 要求，原有付款设置、原生手势及 Root 授权检测保持不变；钱包说明仅改为“息屏时双击电源键呼出Google钱包”。

最新 outputs/Coogle-1.3.3-local-test.apk SHA-256 a2d1c8f77b8125fcda7bc1eeee9e96ebbdc509ca83ecdb200cb9b6db2c263d5e。当前仅国际版参考机在线，未进行国行安装及未授权安装的实机复测。没有提交、推送、更新 Release 或重启设备。
