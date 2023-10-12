const fs = require('fs');

// 修改atom-components组件
const packagePath = './package.json';
let packageData = fs.readFileSync(packagePath).toString();
packageData = packageData.replace('"@tencent/devops-atom-components": "1.0.23",', '"bkci-atom-components": "1.0.0",');
fs.writeFileSync(packagePath, packageData);

const atomCompList = [
  './package.json',
  './src/main.js',
  './src/Atom.vue',
  './src/components/Async.vue',
  './src/components/Basic.vue',
  './src/components/Issue.vue',
  './src/components/Report.vue',
  './src/components/Scan.vue',
  './src/components/Shield.vue',
];
atomCompList.forEach((item) => {
  let fileData = fs.readFileSync(item).toString();
  fileData = fileData.replace(/@tencent\/devops-atom-components/g, 'bkci-atom-components');
  fs.writeFileSync(item, fileData);
});

// 修改组件库
const magicVueList = [
  './package.json',
  './src/i18n/index.js',
  './src/main.js',
];
magicVueList.forEach((item) => {
  let fileData = fs.readFileSync(item).toString();
  fileData = fileData.replace(/@tencent\//g, '');
  fs.writeFileSync(item, fileData);
});

// 设置部署环境
let deployEnv = fs.readFileSync('./src/constants/env.js').toString();
deployEnv = deployEnv.replace(/const DEPLOY_ENV = 'tencent'/g, 'const DEPLOY_ENV = \'github\'');
fs.writeFileSync('./src/constants/env.js', deployEnv);

