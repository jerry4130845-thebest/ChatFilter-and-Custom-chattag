# 纯客户端聊天过滤与玩家标签 · 实施方案

沿用当前工程：**Minecraft Java 1.21.1 / NeoForge 21.1.235 / Java 21**。只改变本机的聊天栏和文本编辑体验；服务器和其他玩家不需要安装。用户已确认按下列范围连续实现、验证并打包；超出范围须先确认。

## 玩家实际会看到什么

| 情形 | 预期 |
|---|---|
| 别人发 `L`、`LLLLLLLL`、`LLLL!!!@!@!@` 或 `（l！L？）` | 整条正文只显示一次 `love you`。只含 L/l、空白、Unicode 标点，且至少有一个 L/l 才触发。`Hello`、`LOL`、`看 L`、`L1`、`!!!` 不误伤。 |
| 自定义词条 | 整句或字面片段；可替换、遮词或隐藏该条聊天，可增删改、启停、调整顺序。默认含刷 L 的专门规则、`ez → gg`、`noob → ***`、隐藏整句 `垃圾队友`。用户不必写正则。 |
| 为账号 `qaz` 配置前后缀 | 名字显示为 `[GOOD PERSON] qaz [ciallo~]`，聊天外层仍按原版语言和样式显示。只看这一条的真实发言人，别人提到 `qaz` 不会被改。不改 TAB、头顶名或计分板。 |
| 输入 `§a§l[GOOD PERSON]` 作前缀 | 原版绿色粗体，不串到用户名或正文；`§0–9/a–f` 切色并重置修饰，`§k–o` 添加修饰，`§r` 恢复该片段的基准样式，大小写均可。预览与实际聊天共用实现。 |
| 原版自由文本框 | 允许键入、粘贴 `§`，包括聊天/命令、铁砧、普通及悬挂告示牌、书页与书名。只额外允许这一个字符，长度、只读、换行及控件自己的合法性校验仍有效。数值或资源 ID 不变成任意文本。 |
| 发聊天 `§a你好§r` 或命令 `/msg qaz §b你好` | **总是先去格式，再按原通道正常发送**：分别发 `你好` 和 `/msg qaz 你好`。删除合法格式码及残余 `§`，未知码后的普通字符保留；清洗后为空不发。无“原样发送”开关。书、告示牌、铁砧的联机保存由服务器决定，不承诺他人可见颜色。 |
| 简易 GUI | **Mods → 聊天过滤与自定义标签 → 配置**。词条和玩家标签两页，原文/效果预览，保存后即时生效，取消不落盘。配置只写当前客户端实例 `config/chatfilter_chattag.json`；坏配置或保存失败有提示，不用默认值静默覆盖原文件。 |

## 修改范围（相对项目根目录）

`J` = `src/main/java/dev/chatfilter/chattag`，`R` = `src/main/resources`。不另建工程，不加库，不安装包，不写 Git，不改真实游戏配置、已有测试、`.env` 或其他项目。**如需超出下表，先停下来说明差异、原因，再等确认。**

| 阶段 | 范围 |
|---|---|
| 方案/结果 | 仅根目录 `plan.md`；交付时补真实结果及未测项。 |
| B · 模板 | `gradle.properties` 仅改 `mod_id=chatfilter_chattag`、`mod_name`、`mod_group_id=dev.chatfilter.chattag` 三个身份字段；`R/META-INF/neoforge.mods.toml` 仅改说明和启用 `[[mixins]]`。停用并移除四个示例文件：`src/main/java/com/example/examplemod/{ExampleMod,ExampleModClient,Config}.java` 和 `R/assets/examplemod/lang/en_us.json`。新建唯一纯客户端入口 `J/ChatFilterClient.java`、接线 `J/client/ClientFeatures.java`、`R/chatfilter_chattag.mixins.json`；不注册方块、物品、服务端事件或网络通道。 |
| C · 功能 | 接手 B 的接线文件和 Mixin 列表；`J/core/{Settings,RuleEngine,OutboundText}.java`，`J/client/{ChatPresenter,LegacyText,TextInput,ClientConfigStore,SettingsScreen}.java`，`R/assets/chatfilter_chattag/lang/{zh_cn,en_us}.json`；`J/mixin/client/{ChatListenerMixin,EditBoxMixin,MultiLineEditBoxMixin,MultilineTextFieldMixin,TextFieldHelperMixin,BookEditScreenMixin,ClientPacketListenerMixin}.java`。 |
| D · 验证 | 现有 wrapper 和依赖只读。允许构建自己的 `build/`、`.gradle/`、隔离开发实例 `run/client/`、临时断言与真实 patch（放在忽略的 `build/verification/` 或临时目录）；不改仓库测试。源文件修正仅限上述白名单。 |

## 实现约束与依据

- 现有 `build.gradle` 是 **NeoGradle userdev 7.1.38**，wrapper **9.2.1**。入场已有 ` M build.gradle`（三条 Maven 仓库），SHA-256 `f761f9590b3c7ae7c3c4df90e08a7fe05f30cba30e421be27bd7999d0bc6475c`，不碰、不还原、不计入本次 diff。
- 按[该版 NeoForge 源与补丁](https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.235/)及 [Mojang 1.21.1 映射](https://piston-data.mojang.com/v1/objects/2244b6f072256667bcd9a73df124d6c58de77992/client.txt)核实调用点。入站在 `ChatListener.showMessageToPlayer` 的两处 `ChatComponent.addMessage(component, signature, tag)` 只改 HUD 参数或跳过该次添加；不取消整个玩家聊天流程，不修改签名、确认、举报、撤回和原版旁白。保留服务器遮罩；不从签名原文重建已被过滤的内容。
- 仅原版普通玩家聊天 `ChatType.CHAT`，排除自己、系统、动作栏、私聊、队聊和不可识别的插件格式。按实际 GameProfile/UUID 匹配账号（账号名不区分大小写，也支持按 UUID），只装饰 `sender` 槽。保留原版译键、兄弟组件和未修改文字的颜色/点击/悬浮；未知结构原样放行。词条在可见正文上匹配，遮罩不可跨越，不把转换结果再递归过滤。
- 输入侧只改客户端 `EditBox`、多行框、`TextFieldHelper`、`BookEditScreen` 的键入和粘贴路径，不放宽公共 `StringUtil` 或内置服务端校验。书名若无原生粘贴分支则补标准粘贴并复用其原校验。出站在普通聊天 `ClientChatEvent` 和命令 `ClientPacketListener.sendCommand/sendUnsignedCommand` 签名前清洗，不构造网络包、不改变权限、不让普通聊天因清洗露出 `/` 而变成命令。
- GUI 用原版控件和 NeoForge `IConfigScreenFactory`。用游戏自带 Gson 和 JDK 做本地配置的校验、临时写入和替换；无需 Cloth Config 等第三方依赖。

## 验收与交付

1. B 核对客户端入口、元数据和空 Mixin 清单，确认示例注册移除、原构建脚本哈希不变；出示本阶段真实 diff。
2. C 用 JDK 21 临时断言调用实际生产代码，验证上表 L 正反例、遮词/替换/隐藏、非递归、发言人而非正文、§ 合法/未知/孤立码、空聊天/命令、配置往返/异常不覆盖；收集相对 B 的真实 diff 和实际输出。无需改测试或装包。
3. D 通过 `mc_gradle` 调用本工程 wrapper，构建等价命令 `gradlew.bat build -Djava.io.tmpdir=C:\Temp --offline`。检查 `build/libs/chatfilter_chattag-1.0.0.jar` 的 ID、客户端注入、语言资源和示例移除；在隔离开发客户端验证 Mixin、GUI 和输入。缺缓存先报告，不改仓库/依赖来掩盖。
4. 联机双端观察、举报与撤回需要获准的无模组测试服和第二位玩家；环境不存在则标为**未实测**，不擅自使用用户账号或连接外服。交付可安装 JAR、使用入口、真实构建/验证结果与补丁；不把编译通过说成联机实测。

## 交付记录

- 产物：[chatfilter_chattag-1.0.0.jar](<build/libs/chatfilter_chattag-1.0.0.jar>)。适用 MC 1.21.1 / NeoForge 21.1.235（元数据接受同游戏版本下不低于该值的 NeoForge）；只放客户端 `mods/`。从 Mods → 聊天过滤与自定义标签 → 配置进入，支持按账号名或 UUID 设标签。
- 实际差异：[骨架 B](<build/verification/B.patch>)、[功能 C](<build/verification/C.patch>)。现有构建脚本与原依赖未改；界面文案中英各 42 个键。
- 实际验证：[C 临时探针](<build/verification/C-probe.txt>) 82 项断言通过；[隔离客户端运行探针](<build/verification/D-client-probe.txt#L14-L41>) 26 项检查通过，包含游戏主界面到 Mods 配置入口、刷 L 预览、`§` 输入、绿色粗体标签预览、取消不写文件及七个客户端 Mixin 加载。运行探针通过代码调用控件，不能替代所有屏幕的手动键入与剪贴板测试。
- 未实测：未装模组的外部服务器双端聊天、举报/撤回、告示牌/书/铁砧联机回读；未知聊天插件格式和未知文本控件保持原行为。

## 补修：实际 `§` 输入与联机本地显示（已确认）

- 用户实测：剪贴板和 Windows `Alt+0167` 都不能输入 `§`。上次只调用了配置框的控件方法，没有覆盖系统剪贴板或真实键盘事件，不能据此判定已经修好。当前只按屏幕和字段标签放行；先收集失败界面、对照键盘与粘贴实际路径，不假定是输入法、字体或其他模组的问题。
- 实现目标：在真实聊天框与标签编辑框验证粘贴后的编辑缓冲区和屏幕显示，保持普通文字行为。为支持的自由文本框补一个游戏内插入 `§` 的快捷键，标签 GUI 可加「插入 §」按钮；不依赖 Windows Alt 数字码能否到达游戏。结构化输入和原版合法性/长度校验不放宽。
- 联机含义：加入服务器照样对本机显示做词语过滤和账号标签；服务器仍执行原版聊天与命令，其他人看到原文；不传规则、标签或客户端美化后的内容，不新增模组网络包。按已选行为，聊天/命令带格式码时只发送原版纯文本。若还要求自己的带格式发言在本机聊天记录中显示颜色，须单独确认；不默认为此维护本地回显或更改其他原版同步。
- 待批范围：`J/client/{TextInput,SettingsScreen}.java`、`J/mixin/client/{EditBoxMixin,MultiLineEditBoxMixin,TextFieldHelperMixin,BookEditScreenMixin}.java`、`R/assets/chatfilter_chattag/lang/{zh_cn,en_us}.json`。若复现确认为聊天屏本身绕过控件，可新增 `J/mixin/client/ChatScreenMixin.java` 并仅更新 `R/chatfilter_chattag.mixins.json` 对应声明。不改入站匹配规则、签名和发送逻辑、依赖、构建脚本、已有测试及真实游戏配置；范围还需扩大时先停。
- 验收：在隔离客户端用实际粘贴和游戏内插入路径，而不只调用 `EditBox.insertText`；检查显示、编辑缓冲、未支持字段不受影响。离线构建并给实际 diff。若有获准的原版测试服与第二端，对照同一条聊天在两个客户端的显示，核验服务器只处理普通聊天、无美化数据；环境不具备则如实标作未实测，不接入用户的外服。

### 新增范围：自己的普通发言只在本机着色（已确认）

- 用户补充：未显示的是自己的账号前缀。旧版 `ChatPresenter` 明确排除本机发言；在该已获批修改文件中取消对自己的标签排除。自己的正文仍不走过滤词条；按原版可识别的真实发言人处理自己的标签，不按聊天行猜身份。

- 用户确认报错位置是模组设置界面，并选择「自己看到颜色，其他人仍看纯文本」。输入修复优先覆盖配置页的前缀/后缀；保留真实聊天框的补测。
- 发 `§a你好` 时，依原版只发送一条纯文本 `你好`；客户端暂存本机样式，不传服务器，不落盘。不预先添加一条假的聊天记录；收到服务器正常返给自己的普通玩家聊天后，只替换本机那一行的正文样式，不动发送者、签名、确认、举报、撤回或旁白。命令照旧清洗，不把命令结果重染；服务器未回显、遮罩/改写、识别不出原版发言或关联不确定时保留原显示。
- 已核对 `ClientPacketListener.sendChat` 是 NeoForge 出站事件、签名前清洗、原版 `ServerboundChatPacket` 的路线；未签名服务端可重建时间与盐，不能把本机时间/盐当成所有服务器的稳定 ID。已签名时按当前连接和消息签名精确关联；无签名时仅允许短时、同一连接、真实本人、可唯一关联的普通纯文回显。内存有限长/过期/随连接清除，不能跨服或把别人同文着色。
- 申请在上一段输入白名单之上增改 `J/client/{ClientFeatures,ChatPresenter,LegacyText}.java`、`J/mixin/client/{ClientPacketListenerMixin,ChatListenerMixin}.java`，新增 `J/client/LocalChatStyle.java`；仅为本机关联和样式渲染，不增加任何网络协议、包、频道或服务端修改。沿用构建依赖、规则配置和现有测试；如实现需要其他源文件或版本属性，暂停另批。
- 验收增加：`§a你好` 发出载荷仅 `你好`，匹配本机回显恰好显示一次绿色；另一位玩家同句、同文重复或歧义、无回显、改文、遮罩、换服、普通命令均不误染。原版无模组服验证不了则在隔离探针实测消息和 HUD 入口并列明未测项。

### 新增范围：F5 显示自己的服务端名牌（已确认）

- 仅在 F5 第三人称、相机仍跟随本机角色时，让本机看见自己的头顶名牌；包含单机、离线账号进服及正常联机，不按是否登录微软账号设门槛。与「自定义聊天标签」分开：名牌使用客户端已经同步的服务端原版显示数据，不把本地聊天前后缀强加到头顶。
- 1.21.1 原版 `LivingEntityRenderer.shouldShowName` 因自己的角色是相机实体而不画名牌；NeoForge 的 `RenderNameTagEvent` 已拿到 `getDisplayName()`。原版网络处理已经把队伍前后缀和颜色更新到客户端记分板，也正常处理已发到本机的实体元数据。复用该渲染事件和现有客户端状态，不截取/重解析原版包、不注册自定义包；服务端用不下发的私有数据或特殊插件协议造的名牌不猜测支持。
- 优先保留已有渲染事件组件；沿用原版队伍前后缀、颜色、名字下方分数与样式；如果确实同步了玩家实体的 CustomName，可使用同步名而非本地猜名字，也保留原版队伍装饰。尊重服务端队伍隐藏名牌设置、其他渲染器明确禁止、原版隐藏 HUD 等条件，不强制揭示隐藏信息。
- 申请范围：`J/client/ClientFeatures.java` 注册事件，新增 `J/client/SelfNameTag.java`；不改入站网络处理、实体数据、服务端、依赖和构建。验证第一人称不显示、第三人称自己显示、前后视角、原版队伍前后缀更新、真实离线开发身份、服务端设为隐藏后不显示、其他玩家不被修改。

### 补修交付状态

- 三片已实现并生成[同名修复版 JAR](<build/libs/chatfilter_chattag-1.0.0.jar>)，SHA-256 `98e9cd0dadba2405e4e27b92636115b3761bfa11df83018adcea39bbe8e6bb16`。用新版替换旧包，不要同时安装。
- 真实差异：[输入](<build/verification/input-fix.patch>)、[本人聊天](<build/verification/local-chat.patch>)、[F5 名牌](<build/verification/self-nametag.patch>)。输入快捷键为 `Ctrl+Shift+F8`；前后缀输入框旁也有插入按钮。
- 因用户机器内存不足，已终止该轮开发客户端验证。不要把打包和源码检查视为系统剪贴板、F5 或多人联机实测。

## 再修：多人聊天前后缀仍不显示（待确认，先交文档）

**复现和目标。** 用户用上版修复包实测：自己设置的聊天前后缀，单人有效，多人无效。目标是联机也只在本机聊天栏显示前后缀；原版聊天与命令正常发送，服务器和别的玩家不应收到美化内容。不改 F5、TAB、头顶名、输入快捷键或已完成的出站清洗。

### 目前能确认什么

- 现版已不排除实际本人，但 [`ChatPresenter`](<src/main/java/dev/chatfilter/chattag/client/ChatPresenter.java#L23-L49>) 同时要求原版 `ChatType.CHAT`、预期译键、发言人和正文槽、原版正文形态。任何条件不符都会原样显示；甚至正文无法解析时，名字也一起跳过。这个限制能解释单人成功、某些联机失败，但**还没有该服实际消息数据，不能认定是某种插件或包型**。
- 1.21.1 的 [客户端入站分流](<build/neoForm/neoFormJoined1.21.1-20240808.144430/steps/transformSource/transformed/net/minecraft/client/multiplayer/ClientPacketListener.java#L939-L984>) 显示：`PlayerChat` 有发言人 UUID；`DisguisedChat` 有显示结构却没有同等的发言人 UUID；`SystemChat` 可只有显示组件。后两路当前不进本模组的玩家标签路径。离线服、代理或不同账号环境还可能让同一账号在不同连接上的 UUID 不同，因此诊断时需区分配置里用的是账号名还是 UUID。

### 拟修范围（仅在确认后执行）

1. **先修能可靠识别的一路。** 在本地聊天 HUD 上，把「给已确认的发言人加标签」与「解析/过滤正文」拆开。原版结构保持不变；对已验证账号的玩家包，仅在存在可识别且唯一的发言人显示槽时插入前后缀；即使正文被服务端重排或包含无法安全解析的组件，也应尽可能保留原文并给名字加标签。不把正文明文中的名字当发送者；`§` 本人回显仍使用更严格的匹配。账号名和 UUID 按该连接实际身份匹配，UUID 规则不偷偷改按显示名。
2. **按样例决定插件格式回退。** 如果服务器给的是拟态或系统聊天，先核对实际聊天长相。不全局扫描公告、私聊或任意 JSON；仅建议为确认的服务器格式做明确、默认关闭的本地适配，要求固定布局、明确作者槽以及唯一在线账号对应。拟态/系统路径没有原版发言人证明：名字不唯一、仅有昵称、无样例、布局不匹配时原样显示，不猜身份，也不做本机彩色回显。若服务端连可区分的作者信息都没发来，纯客户端不能可靠复原它。用户只需提供一条普通聊天栏原样或截图（保留括号、分隔符和身份前缀，账号/地址可以打码），不需要管理权限、服务端日志或插件列表。
3. **先划定文件。** 第一步候选白名单：`J/client/ChatPresenter.java`、`J/mixin/client/ChatListenerMixin.java`；仅在核实需要时可改 `J/mixin/client/ClientPacketListenerMixin.java` 的入站关联，不动发送路径。第二步若确需按服适配，先凭样例确定数据入口与文件范围，再等批准；本方案不先授权配置/GUI 持久化改造。无新的数据包、频道、服务端插件、依赖、测试文件、真实游戏配置或 Git 写操作。
4. **验收与打包。** 静态核对和低内存样例验证：单人及原版玩家包本人/他人、服务端自定义消息组件、身份缺失/重复、正文提及名字、公告、动作栏、私聊、过滤遮罩、跨服切换。保留样式、组件交互、签名、确认、举报和撤回；不新增、重复显示聊天。确认后才用 `mc_gradle build`，参数 `-Djava.io.tmpdir=C:\Temp --offline --no-daemon --max-workers=1`，交实际 diff、构建结果与 JAR；按上次内存要求，不启动 Minecraft 或测试服。没有真实多人验证时明示，不用构建成功冒充联机修好。

### 按服务器配置的格式兼容（用户已确认）

- 保留上一轮可信玩家包处理；另增默认关闭的纯客户端回退。用户在 Mods 配置页按实际连接地址添加、删除和编辑配置，各服独立；模板例如 `[会员] {name}: {message}`，可选 `[{rank}] {name}: {message}`。地址仅本地精确归一匹配（主机名大小写和默认端口），不连接、不查 DNS。自动使用当前服务器提供的登录账号名作 `{name}`，可按服填明确的显示昵称覆盖（仅用于该模板，不改变配置中的玩家账号/UUID）。
- 从行首严格匹配模板，只检查唯一的作者槽；`{message}` 必须在模板末尾，正文里的任何昵称、冒号都不参与。`[会员] impro: qaz wsp` 不加本用户标签；`[会员] qaz: wsp` 只围绕 `qaz` 加对应玩家标签。不匹配、缺配置、单机、无标签及动作栏不动。对没有发言人元数据的消息这是明确启用的排版推断，并非身份认证；服务器若把公告或仿冒内容发送成完全相同的格式，纯客户端无法绝对区分。
- 白名单：`J/core/{Settings,ServerChatFormat}.java`、`J/client/{ClientConfigStore,SettingsScreen,ServerChatTags}.java`、`J/mixin/client/ChatListenerMixin.java`、对应中英语资源与 `README.md`。沿用客户端配置的原子保存、读旧版本；不碰实际用户配置、入站玩家签名路径、过滤规则、出站、F5、构建依赖和 Git。仅在系统/拟态聊天显示入口中改变 HUD 组件，不加包，不吞原文，不改动作栏；尽量保留原有样式和交互。格式不安全或不适配保持原样；如需超出白名单先停。
- 验收：离线小探针覆盖正文提名及冒号、前缀边界、归一地址/端口隔离、无配置不改及旧配置升级；`mc_gradle build` 单 worker、离线、`-Djava.io.tmpdir=C:\Temp`；不启动游戏、不连接真实服务器。

### 玩家名颜色与按服自动匹配（用户已确认）

- **玩家标签 → 名字颜色**：可填写原版 `§0`–`§f`，例如 `§6` 是金色；留空或 `§r` 沿用服务器原色。提供格式码插入按钮和实时预览。只覆盖已识别到的玩家名字颜色，不覆盖服务器称号、正文、本地前后缀或原有点击/悬浮和其他样式。名字边界不明确时不把整段身份头强行染色。作用域仍是本机聊天栏，不改 F5、TAB 或其他客户端。
- **服务器格式 → 自动匹配**：在当前地址的配置中填目标名，点「自动匹配」，保存后在下次进该服时继续自动找候选。候选需要记录完整服务器称号以便识别玩家；不读取聊天正文，不发包，不请求额外数据。优先按同一账号的 UUID 或完整名字关联，把去除格式后含有独立目标词的 `[EPIC] qaz` 放在高位；编辑距离等宽松匹配仅用于可靠身份下的候选排序，不自动认领相似的陌生名字。UI 只展示少量候选并可手动选择；其他服、旧连接和正文提名不进入候选，不把整条聊天写到配置。没有收到相应显示信息时列表可以为空，仍能手动填昵称。
- **选择后怎么显示**：选中并保存候选才生效，保留 `[{rank}] {name}: {message}` 一类既有模板；匹配时只检查 `{name}`，必要时把可分离的服务器称号留在原位置。比如 `[EPIC] qaz: hi` 可显示为 `[EPIC] [本地前缀] qaz [本地后缀]: hi`，其中 `qaz` 可为金色；`[EPIC] impro: qaz wsp` 不触发。当前发布版的纯文本兼容继续限定为本人标签；原版有身份的玩家聊天仍可按账号或 UUID 给其他玩家设置标签。不会仅凭相似度给其他玩家套用本人的标签。
- **白名单**：`J/core/{Settings,ServerChatFormat}.java`、`J/client/{ClientConfigStore,SettingsScreen,TextInput,LegacyText,ChatPresenter,ServerChatTags,ServerNameMatches}.java`、`J/mixin/client/ChatListenerMixin.java`（仅候选观察点必要时）、中英语资源及 `README.md`。配置代码读旧版本并保留原有标签、手动昵称、规则和服务器配置；保存才迁移，不直接改用户的真实文件。禁改构建、依赖、出站、过滤、F5、已有测试及 Git；如需扩大范围先停。
- **验收**：验证 `§6` 与恢复原色、称号/正文样式不变、作者槽分离、同名冲突、`qaz2`/正文 `qaz` 不误绑、切服隔离、手动确认、向后兼容；只用低内存离线探针和 `mc_gradle build -Djava.io.tmpdir=C:\Temp --offline --no-daemon --max-workers=1`，不启动游戏或连接服务器。
- **交付结果**：[新版 JAR](<build/libs/chatfilter_chattag-1.0.0.jar>)，SHA-256 `e4d414dd58a48d37debb18a57dfccd196611745d5aad95f23cd6f150f52fc6b5`。单 worker 离线构建 20 秒成功。[离线探针](<build/verification/server-name-C2-probe.txt>)通过候选 54 项和名字颜色回归 91 项，未启动游戏；GUI、Mixin 运行时和实际服务器未实测。阶段真实差异：[独立颜色](<build/verification/name-color-C1.patch>)、[按服匹配](<build/verification/server-name-C2.patch>)。

### 屏蔽词草稿

先交 [可编辑词表](<chat-filter-wordlist.md>)：现有 4 条规则和分类候选分开，全部候选默认不勾选。用户可自行增删、勾选、写明动作；本轮不把草稿导入包、不修改现有默认规则、不覆盖本地配置。等用户改完，再确认要纳入哪些词条、匹配模式与动作。