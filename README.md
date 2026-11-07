# jenkins-atom-files-tool

Files tool — port of **pipeline-utility-steps** (57,942) as an atom.

补的是 utility-steps 里还没做的四个文件操作；json/zip 已由 json-tool、zip-artifacts 提供。

## Build and test

```bash
mvn verify
```

The integration test in `src/test/java/atom/FilesToolIT.java` covers touch, sha256, find and readCSV.
