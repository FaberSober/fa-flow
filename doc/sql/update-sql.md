# 更新FlowLong最新版本数据库同步AI执行提示词
```
更新`fa-flow`模块到最近版本SQL
我已经将`fa-flow\pom.xml`中`flowlong-spring-boot-starter`更新到最新版本。需要你帮我处理数据库同步的工作。
1. 所有的SQL放在`fa-flow\doc\sql`目录下；
2. 要把最新版本对应的SQL更新到`fa-flow\doc\sql\latest`对应的SQL文件中，由于flowlong官方的sql是先delete再create，会造成数据库删除表。所有在`fa-flow\doc\sql\latest`要修改为`CREATE TABLE IF NOT EXISTS`；
3. 要将本次变更的DDL汇总到`fa-flow\doc\sql\flowlong-update.sql`中，而且要在`fa-flow\src\main\resources\sql\fa-flow`目录下生成对应的自动升级SQL脚本；
```