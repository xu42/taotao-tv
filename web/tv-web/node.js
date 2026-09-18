const path = require("path");
const yaml = require('js-yaml');
const fs = require("fs");
const utf8 = "utf8";

/*
 * yml -> json。
 *
 * 应用已改为纯本地运行：不再有服务端资源热更新，因此这里只做一件事——
 * 把频道源数据 js/cctv/tv.yml 转成运行时读取的 tv.json。
 */

function yamlToJson(dir) {
    const files = fs.readdirSync(dir);
    files.forEach(file => {
        if (file.endsWith(".yml")) {
            const jsonData = yaml.load(fs.readFileSync(dir + file, utf8));
            fs.writeFileSync(dir + file.substring(0, file.indexOf(".")) + ".json",
                JSON.stringify(jsonData), utf8);
        }
    });
}

yamlToJson("js/cctv/");
console.log("tv.yml -> tv.json 完成");
