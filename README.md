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

## 许可

见 [LICENSE](LICENSE)。
