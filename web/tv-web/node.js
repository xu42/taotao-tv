const path = require("path");
const yaml = require('js-yaml');
//const toml = require('toml');
const fs = require("fs");
const utf8="utf8";

/*
 * yml -> json。
 *
 * 说明：上游这里会先跑 huo.js 把 tv.yml 转成 tv2.yml（去掉 tv-web 前缀、剔除 huya），
 * 但 tv2.* 从来没有被应用读取过 —— 应用读的是 tv.yml 直接生成的 tv.json，
 * 而且被剥掉 "tv-web/" 前缀的地址反而无法命中内置页面的拦截逻辑。
 * 因此这里不再生成 tv2.*，只做「源数据 -> 运行时 json」这一件事。
 */

function  yamlToJson(path){
    fs.readdir(path, (err, files) => {
        files.forEach(file => {
            //let name = file.name;
            if(file.endsWith(".yml")){
                let  jsonData=  yaml.load(fs.readFileSync(path+file, utf8));
                fs.writeFileSync(path+file.substring(0,file.indexOf("."))+".json",JSON.stringify(jsonData),utf8);
            }
          /*  if(file.endsWith(".toml")){
                let  jsonObj=  toml.parse(fs.readFileSync(path+file, utf8));
                fs.writeFileSync(path+file.substring(0,file.indexOf("."))+".json",JSON.stringify(jsonObj),utf8);
            }*/
        });
    });
}

yamlToJson("js/cctv/");
const yamlString = fs.readFileSync('update.yml', utf8);
const jsonData = yaml.load(yamlString);
fs.writeFileSync("update.json",JSON.stringify(jsonData),utf8);
console.log("node "+JSON.stringify(jsonData));