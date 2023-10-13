/**
 * @file main entry
 */

import Vue from 'vue';
import LocalAtom from './data/LocalAtom';
import PublicAtom from './data/PublicAtom';
import bkMagic from 'bk-magic-vue';
import bkciAtoms from 'bkci-atom-components';
import VeeValidate from 'vee-validate';
import request from '@/utils/request';
import validDictionary from './utils/validDictionary';
import ExtendsCustomRules from './utils/customRules';
import store from './store/index';
import i18n from './i18n';

// 全量引入 bk-magic-vue 样式
require('bk-magic-vue/dist/bk-magic-vue.min.css');
// 如需用到代码编辑组件atom-ace-editor时需引用，如果不需要用到则可不引入这个文件
require('bkci-atom-components/dist/brace.js');

require('./css/conf.scss');

Vue.use(bkMagic);
Vue.use(bkciAtoms);

Vue.prototype.$ajax = request;


VeeValidate.Validator.localize(validDictionary);
Vue.use(VeeValidate, {
  fieldsBagName: 'veeFields',
  locale: 'cn',
});
ExtendsCustomRules(VeeValidate.Validator.extend);
console.log('🚀 ~ file: main.js ~ line 35 ~ ', ExtendsCustomRules(VeeValidate.Validator.extend));

global.atomVue = new Vue({
  el: '#pipeline-atom',
  i18n,
  components: {
    PublicAtom,
    LocalAtom,
  },
  store,
  template: `${ISLOCAL ? '<LocalAtom/>' : '<PublicAtom/>'}`, // eslint-disable-line
});
