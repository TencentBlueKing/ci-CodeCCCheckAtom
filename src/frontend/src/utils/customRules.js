/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT License.
 *
 * License for BK-CI 蓝鲸持续集成平台:
 *
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
*/
import i18n from '../i18n';

const customeRules = {
  string: {
    validate(value, _args) {
      return /^[\w,\d,\-_()]+$/i.test(value);
    },
  },
  unique: {
    validate(value, args) {
      let repeatNum = 0;
      for (let i = 0; i < args.length; i++) {
        if (repeatNum > 2) return false;
        if (args[i] === value) {
          repeatNum += 1;
        }
      }
      return repeatNum <= 1;
    },
  },
  ruleSetRequired: {
    validate(value, _args) {
      return typeof value === 'object' && value.length;
    },
  },
  asyncTaskRequired: {
    validate(value, _args) {
      return value;
    },
  },
  scriptRequired: {
    validate(value, _args) {
      const defValue1 = i18n.t('# Coverity/Klocwork将通过调用编译脚本来编译您的代码，以追踪深层次的缺陷\n# 请使用依赖的构建工具如maven/cmake等写一个编译脚本build.sh\n# 确保build.sh能够编译代码\n# cd path/to/build.sh\n# sh build.sh');
      const defValue2 = '';
      return value !== defValue1 && value !== defValue2;
    },
  },
  markVariable: {
    validate(value, _args) {
      return /^[\w-]*$/.test(value) || /^\$\{.*\}$/.test(value);
    },
  },
  regexVariable: {
    validate(value, _args) {
      try {
        new RegExp(value);
        return true;
      } catch (e) {
        return false;
      }
    },
  },
  // customPath: {
  //     validate: function (value, args) {
  //         return /^\.\*.*/gi.test(value)
  //     }
  // }
};

function ExtendsCustomRules(_extends) {
  if (typeof _extends !== 'function') {
    console.warn('VeeValidate.Validator.extend must be a function');
    return;
  }
  Object.keys(customeRules).forEach((key) => {
    if (customeRules[key]) {
      _extends(key, customeRules[key]);
    }
  });
  // for (const key in customeRules) {
  //   if (customeRules.hasOwnProperty(key)) {
  //     _extends(key, customeRules[key]);
  //   }
  // }
}

export default ExtendsCustomRules;
