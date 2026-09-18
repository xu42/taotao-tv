const args = process.argv.slice(2);
var hun = require('javascript-obfuscator');
const archiver = require('archiver');
const fs = require("fs");
const yaml = require('js-yaml');
let isHun =true;
if(typeof args[0]!=="undefined"){
       // throw new Error('args must not be empty.');
        console.log(args[0]);
        if(args[0]==="false"){
                isHun=false;
        }
}
let isLog=!isHun;
console.log("isHun "+isHun+" isLog "+isLog);
let basePath="D:/work/utao/utao/web/tv-web";
//let toPathBase="D:/work/androidx/gecko/app/src/main/assets";
let toPathBase="D:/work/web/gen";
let toPath=toPathBase+"/res";
const utf8="utf8";
let option={
        compact: true,
        controlFlowFlattening: false,
        deadCodeInjection: false,
        debugProtection: false,
        debugProtectionInterval: 0,
        disableConsoleOutput: true,
        identifierNamesGenerator: 'hexadecimal',
        log: isLog,
        numbersToExpressions: false,
        renameGlobals: false,
        selfDefending: true,
        simplify: true,
        splitStrings: false,
        stringArray: true,
        stringArrayCallsTransform: false,
        stringArrayEncoding: [],
        stringArrayIndexShift: true,
        stringArrayRotate: true,
        stringArrayShuffle: true,
        stringArrayWrappersCount: 1,
        stringArrayWrappersChainedCalls: true,
        stringArrayWrappersParametersMaxCount: 2,
        stringArrayWrappersType: 'variable',
        stringArrayThreshold: 0.75,
        unicodeEscapeSequence: false
};
//let data = fs.readFileSync(basePath+"js/index.js", "utf8");
/*var obfuscationResult = hun.obfuscate(
    data,
    option
);*/
//console.log(obfuscationResult.getObfuscatedCode());
//fs.writeFileSync("index2.js",hun.getObfuscatedCode(),"utf8")
let currentTime = new Date().getTime();
let yaOld={
        work(src,to){
              fs.readFileSync(src, utf8,function (err,data){
                        fs.writeFileSync(to,hun.obfuscate(data, option).getObfuscatedCode(),utf8)
              });
        },
        yamlToJson(basePath){
                //let basePath="D:/work/web/tv-web";
                const yamlString = fs.readFileSync(basePath+'/update.yml', utf8);
                const jsonData = yaml.load(yamlString);
                fs.writeFileSync(basePath+"/update.json",JSON.stringify(jsonData),utf8);
                console.log(JSON.stringify(jsonData));
                fs.readdir(basePath+"/data", (err, files) => {
                            files.forEach(file => {
                                    //let name = file.name;
                                    if(file.endsWith(".yml")){
                                            let  jsonData=  yaml.load(fs.readFileSync(basePath+'/data/'+file, utf8));
                                            fs.writeFileSync(basePath+'/data/'+file.substring(0,file.indexOf("."))+".json",JSON.stringify(jsonData),utf8);
                                    }
                            });
                });
        },
        rootPath(basePath,toPath){
                basePath=basePath+"/";
                toPath=toPath+"/";
                fs.readdir(basePath, (err, files) => {
                        files.forEach(file => {
                                let stat = fs.statSync(basePath+file);
                                if(stat.isDirectory()){
                                        if(!yaOld.notInPath(file)){
                                                this.mkdir(toPath+file)
                                                this.rootPath(basePath+file,toPath+file);
                                        }
                                }else{
                                        yaOld.writeFile(basePath+file,toPath+file);
                                }
                        });
                });
        },
        notInPath(folder){
                let folders= [".git",".idea","doc","test","back","web-ext-artifacts","node_modules"];
                return folders.indexOf(folder) !== -1;
        },
        writeFile(src,to){
                //console.log("writeFile "+src+" "+to);
                if(src.endsWith(".js")||src.endsWith(".html")){
                        fs.readFile(src, utf8,function (err,data){
                                //逻辑处理 版本替换逻辑
                                if(src.endsWith(".js")&&!src.endsWith(".min.js")){
                                        if(src.indexOf("load_")>0){
                                                data= yaOld.workVersion(data);
                                        }
                                        if(isHun){
                                                data=hun.obfuscate(data, option).getObfuscatedCode();
                                        }
                                }
                                if(src.endsWith(".html")){
                                        //data= ya.workVersion(data);
                                }
                                fs.writeFileSync(to,data,utf8);
                        });
                }else{
                        fs.readFile(src,function (err,data){
                                fs.writeFileSync(to,data);
                        });
                }
        },
        workVersion(data){
                return  data.replaceAll("?v=x","?v="+currentTime);
        },
        mkdir(path){
                //fs.statSync(path)
                //console.log(path);
                try {
                        fs.accessSync(path, fs.constants.F_OK);
                        //console.log('File exists');
                } catch (err) {
                       // console.log('File does not exist');
                        fs.mkdirSync(path);
                }
        }
}
//D:\work\utao\utao\android\x5\app\src\main
//let toAppRes="D:/work/utao/utao/android/app/src/main/assets";
yaOld.yamlToJson(basePath);
yaOld.rootPath(basePath,toPath);
//let tvWeb="tv-web";
 toPathBase="D:/work/utao/utao/android/app/src/main/assets";
 //D:/work/utao/utao/android/app/src/main/assets D:/work/web/gen
 toPath=toPathBase+"/tv-web";
yaOld.rootPath(basePath,toPath);
setTimeout(function (){
        let tvWeb="tv-web";
        let toPathBase="D:/work/web/gen";
        //let toAppRes="D:/work/utao/utao/android/app/src/main/assets";
        function  zip(pathBase,folder){
                // 第二步，创建可写流来写入数据
                const output = fs.createWriteStream(pathBase + "/"+folder+".zip");// 将压缩包保存到当前项目的目录下，并且压缩包名为test.zip
                const archive = archiver('zip', {zlib: {level: 9}});// 设置压缩等级
// 第三步，建立管道连接
                archive.pipe(output);
// 第四步，压缩文件和目录到压缩包中
                archive.directory(pathBase+ "/"+folder, "dist");
// 第五步，完成压缩
                archive.finalize();
        }
        //zip(toPathBase,"tv-web");
      /*  setTimeout(function (){
                fs.copyFileSync(toPathBase+"/"+tvWeb+".zip",toAppRes+"/"+tvWeb+".zip");
                console.log("copy file");
        },1000);*/
},2000);
//fs.copyFileSync(toPathBase+"/"+tvWeb+".zip",toAppRes+"/"+tvWeb+".zip");
