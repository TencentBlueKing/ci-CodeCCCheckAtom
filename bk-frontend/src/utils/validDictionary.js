const dictionary = {
    cn: {
        messages: {
            alpha: field => '字段只能包含字母',
            required: field => '字段不能为空',
            unique: field => '字段不能重复',
            excludeComma: field => '字段不能包含英文逗号',
            string: field => '字段只能包含数字，字母和下划线',
            varRule: field => `只能以字母和下划线开头，同时只包含字母，数字以及下划线`,
            numeric: field => '字段只能包含数字',
            regex: (field, regex) => {
                return `字段不符合(${regex})正则表达式规则`
            },
            max: (field, args) => {
                return `字段长度不能超过${args}个字符`
            },
            min: (field, args) => {
                return `字段长度不能少于${args}个字符`
            },
            max_value: (field, args) => {
                return `最大不能超过${args}`
            },
            min_value: (field, args) => {
                return `最小不能少于${args}`
            },
            ruleSetRequired: field => '规则集字段不能为空',
            scriptRequired: field => '字段不能为空',
            asyncTaskRequired: field => '字段不能为空'
            // customPath: field => '路径请以 .* 开头'
        }
    }
}

export default dictionary
