# legalTraining

legalTraining 提供用户与权限管理、组织管理、通知公告、任务调度、运行监控和代码生成。后端使用 Java 17、Spring Boot、MyBatis、MySQL 和 Redis，前端使用 Vue 2 和 Element UI。

## 本地开发

1. 新建 MySQL 数据库，依次导入 `sql/legalTraining.sql` 和 `sql/quartz.sql`。
2. 在 `legalTraining-admin/src/main/resources/application-druid.yml` 配置 MySQL 连接，并在同目录的 `application.yml` 配置 Redis 和上传目录。
3. 从项目根目录构建并启动后端，默认端口为 8080。

   ```sh
   mvn clean package
   java -jar legalTraining-admin/target/legalTraining-admin.jar
   ```

4. 启动前端，默认访问地址为 http://localhost:80。

   ```sh
   cd legalTraining-ui
   npm install
   npm run dev
   ```

生产环境的前端产物可用 `npm run build:prod` 构建，输出到 `legalTraining-ui/dist`。

## 已有部署升级

停止旧版后端并备份数据库，然后在已有数据库执行 `sql/migrate_legalTraining.sql`。该脚本更新默认品牌数据和已保存的代码生成包名、任务调用目标，可重复执行。

将外部应用配置的项目属性迁移到 `legal-training` 前缀。已有上传文件可通过 `legal-training.profile` 继续使用原来的目录。Druid 监控的默认登录名改为 `legalTraining`。部署新版后，用户需要重新登录。

已有 V3.0 业务库在升级到含成绩异议功能的版本时，执行 `sql/v3_score_appeal.sql`。分组任务升级执行 `sql/v3_task_team.sql`。新装通过 `sql/biz_schema.sql` 建表。

## V3.0 全量备份与独立恢复

`bin/v3-backup.ps1` 使用 PowerShell 7 和 MySQL 8 客户端。备份包含数据库、上传文件与素材、展项版本与发布记录及操作日志。备份目录须位于上传目录之外，并由运维账户控制访问。

如需数据库记录与附件对应到同一时点，在备份期间暂停上传、删除和发布等写入操作。`mysqldump --single-transaction` 一致性覆盖事务表，备份期间也应暂停 DDL。MySQL 账号通过受保护的客户端选项文件配置。

```powershell
pwsh -File bin/v3-backup.ps1 -Action Backup -BackupRoot C:/lt-backups -MysqlBin C:/mysql/bin -ClientOptions C:/private/mysql.ini -Database legal_training -UploadPath C:/lt/uploads
pwsh -File bin/v3-backup.ps1 -Action List -BackupRoot C:/lt-backups
pwsh -File bin/v3-backup.ps1 -Action Restore -BackupRoot C:/lt-backups -MysqlBin C:/mysql/bin -ClientOptions C:/private/mysql.ini -Database legal_training_restore -UploadPath C:/lt/restore-uploads -BackupId <备份编号>
```

恢复前先建立空数据库及空上传目录。脚本检查归档校验值和目标为空，生成恢复前快照，然后导入数据库和素材。用独立应用实例连接恢复库和附件目录验证登录、查询和附件访问。失败后保留该恢复目录和数据库供排查，重试使用新的空目标。

### 增量归档和时点回放

以 `-WithBinlog` 生成全量备份后，使用 `bin/v3-backup-increment.ps1 -Action Increment` 和同一组连接及目录参数归档二进制日志。可指定 `-BackupId` 选择全量基线，省略时使用当前源库最新的手动全量备份。使用 MySQL 8.0、启用 ROW 格式与二进制日志，并保留未被归档的日志文件。源账号需要读取日志坐标和流式读取二进制日志的权限。

`-Action Replay` 接受增量编号和独立空目标。指定 `-RecoverBeforeUtc 2026-11-15T02:26:53Z` 时，应用该秒之前的日志事件。留空时应用到增量归档坐标。恢复链应避开跨库 DDL。归档文件含服务器二进制日志和素材内容，仅限运维账户访问。[MySQL 8.0 二进制日志回放手册](https://dev.mysql.com/doc/refman/8.0/en/point-in-time-recovery-binlog.html)

需要覆盖素材新增、替换和删除的时点恢复时，先执行 `sql/v3_media_recovery.sql` 并启用 `legal-training.backup.managed-media: true`，再在暂停写入期间建立新的全量基线。管理端自动为这条备份链启用素材日志，命令行全量及增量备份使用 `-WithMediaJournal`。应用内通用上传、头像上传与删除、授权书写入走素材日志。运维直接修改磁盘、外部转码或外部对象存储写入需要另行留痕。旧备份链在素材变化时仍可按归档点恢复。

`-Action Prune -Database legal_training -RetainDays 30` 清理超过保留天数的归档，保留仍被有效增量依赖的全量基线、源库最新全量和恢复前快照。

### 管理端执行

已有部署执行 `sql/v3_backup_policy.sql`，并在服务器设置以下 `legal-training.backup` 属性。备份和恢复客户端选项文件由运维单独管理。

```yaml
legal-training:
  backup:
    root: C:/lt-backups
    script-directory: C:/legalTraining/bin
    mysql-bin: C:/mysql/bin
    powershell: pwsh
    database: legal_training
    client-options: C:/private/mysql.ini
    uploads: C:/lt/uploads
    managed-media: true
    restore-database: legal_training_restore
    restore-client-options: C:/private/restore-mysql.ini
    restore-uploads: C:/lt/restore-uploads
```

管理员在「平台管理 → 备份恢复」手动执行全量、增量和恢复，管理计划与保留天数，并查看操作账号、开始时间、结束时间和结果。计划默认关闭。启用后按设置生成归档，成功后执行保留清理。恢复前准备独立空数据库和空目录，恢复后使用独立应用实例验收。

## 案例库接入

系统参数 `biz.openapi.caseUrl` 填写第三方案例查询基址。列表适配读取 `GET 基址?pageNum=1&pageSize=10&keyword=...` 返回的 `total` 和 `rows`，详情读取 `GET 基址/{id}`。每条案例包含 `id`、`title`，可附带 `summary`、`source`，详情可附带 `content`。实际提供方协议与此不同的部署需要适配这些字段。

线上接入使用 HTTPS。提供方需要 Bearer 凭证时，在服务端配置 `legal-training.case-library.token`。对外列表及详情仍通过开放网关的 OP-02-08 签名授权，提供方异常时返回 `degrade: true`。

已有业务库执行 `sql/v3_case_reference.sql`。教师可在课程、任务列表管理「关联案例」，展厅编辑可在展项列表管理。学生仅能查看有课程或任务访问权限的关联案例。公开展项以已发布版本为准，修改引用后随展项重新发布。访客端在案例库不可用时隐藏案例区。

## 展厅全景与推荐推送

已有业务库执行 `sql/v3_panorama.sql`。在展区编辑页填写 2:1 等距柱状全景图地址，素材服务需允许展厅源读取图片。在「编排参观动线」调整顺序，从展区行的「热点」配置位置、目标及图标。热点坐标按原始全景图宽高归一化。观众可从首页开始漫游，按顺序切换展区。部署时可用 `VITE_GALLERY_BGM_URL` 配置已授权的背景音乐素材，支持站内 `/profile/upload/` 或 HTTPS 地址。沉浸版由观众开启音乐。

展馆平面图通过 `VITE_GALLERY_PLAN_URL` 配置图片，`VITE_GALLERY_PLAN_MARKERS` 填写展区编号和图上归一化坐标的 JSON，例如 `{"hall-a":[0.32,0.55]}`。公开平面图上的链接仅展示已开放展区，并高亮当前位置。

跨部署成果推送执行 `sql/v3_rec_push.sql`，设置 `legal-training.recommendation-push.endpoint`、`app-id` 和 `app-secret`。接收地址为接入方 HTTPS 的 OP-02-05 接口。授权事务写入待推送记录，发送失败按指数退避，首次发送失败后最多重试五次。接收端按推荐编号幂等处理，失败耗尽后教师可看到送达失败。单部署保留系统内直接送审。

已有业务库升级时执行 `sql/v3_rec_inbox.sql`。教学侧与展厅侧独立建库时，两端都执行迁移。在展厅侧给教学发送应用授予 OP-02-05，在教学侧给展厅回写应用授予 OP-02-06。教学侧另设 `legal-training.recommendation-push.independent-receiver=true`，并用同一前缀配置 `callback-app-id` 为展厅回写 AppID。展厅侧用 `legal-training.recommendation-inbox` 配置 `source-app-id`（教学发送 AppID）、`callback-endpoint`（教学侧 HTTPS 的 `/openapi/v1/rec/callback`）、`callback-app-id` 和 `callback-app-secret`。凭证由部署密钥系统提供。

展厅侧在「成果审核 → 接收的成果」查看授权范围和确认时间，按需下载正式提交版本的附件和授权书。下载由展厅服务签名访问教学侧，教学侧检查推荐和授权状态。两端使用相同的登录账号标识以执行推荐人回避。审核通过并收到教学侧确认后可选择本馆展区生成草稿，回写队列送达审核决定、展项编号和后续发布及下架状态。学生撤回授权后，教学侧立即关闭证据下载并发送撤回事件，展厅侧移除待审展示并将关联展项下架。

## 开放接口授权

在「平台管理 → 权限管理 → 接口授权」注册应用并保存一次性 AppSecret。分别设置应用总配额、接口配额和 IP 白名单。开放接口按服务端确认的远程地址识别调用方 IP。反向代理部署需使用可信代理的容器层地址解析（如 Tomcat RemoteIpValve），并由入口代理清洗转发头。服务端日志按接口合并五分钟内重复的开放接口 5xx 告警，可据日志记录 ID 查看调用详情。

管理员可在「接口版本」设置通知与下线时间（北京时间）。通知期间已认证调用收到 `Deprecation` 和 `Sunset` 响应头，签名请求 `GET /openapi/v1/version` 可读取说明。到期后 v1 业务接口返回 410，版本说明仍可访问。撤销计划后恢复 v1 业务调用。

## 终端运维

已有终端部署执行 `sql/v3_device_command.sql` 后，管理员可在终端设备页请求截图、重启展厅浏览器或重启终端系统。桌面终端需运行 `bin/v3-kiosk-agent.mjs`，并安装 Node.js 和 Playwright CLI 所用的 Chromium。配置文件提供 `url`（展厅的 `/kiosk`）、`commandBase`（后端 `/gallery/v1/device`）、`profile`（专用浏览器用户目录），可选 `browserBinary` 指定本机浏览器。运行器从已激活的浏览器读取终端凭证，也可通过受保护的 `credentialsFile` 预配。

Windows 或 Linux 专用终端要执行系统重启，需在本机配置 `allowSystemReboot: true`，赋予运行账户系统重启权限，并配置开机自动启动到可显示展厅的会话，继续使用同一 `profile`。运行器比较重启前后的系统启动标识后回执。Android 终端需要现场设备管理方案。

## 许可

见 [LICENSE](LICENSE)。
