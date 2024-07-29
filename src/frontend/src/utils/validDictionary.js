/* eslint-disable no-unused-vars */
import i18n from '../i18n';

const dictionary = {
  cn: {
    messages: {
      alpha: field => i18n.t('字段只能包含字母'),
      required: field => i18n.t('字段不能为空'),
      unique: field => i18n.t('字段不能重复'),
      excludeComma: field => i18n.t('字段不能包含英文逗号'),
      string: field => i18n.t('字段只能包含数字，字母和下划线'),
      varRule: field => i18n.t('只能以字母和下划线开头，同时只包含字母，数字以及下划线'),
      numeric: field => i18n.t('字段只能包含数字'),
      regex: (field, regex) => i18n.t('字段不符合(x)正则表达式规则', { regex }),
      max: (field, args) => i18n.t('字段长度不能超过x个字符', { num: args }),
      min: (field, args) => i18n.t('字段长度不能少于x个字符', { num: args }),
      max_value: (field, args) => i18n.t('最大不能超过x', { num: args }),
      min_value: (field, args) => i18n.t('最小不能少于x', { num: args }),
      ruleSetRequired: field => i18n.t('规则集字段不能为空'),
      scriptRequired: field => i18n.t('字段不能为空'),
      asyncTaskRequired: field => i18n.t('字段不能为空'),
      markVariable: (field, args) => i18n.t('字段只能包含字母，数字，中划线以及下划线或流水线变量'),
      regexVariable: (field, args) => i18n.t('正则表达式不正确'),
      // customPath: field => '路径请以 .* 开头'
    },
  },
};

export default dictionary;
