<template>
    <section class="bk-form">
        <template>
            <div class="atom-txt" v-if="envSupport">
                <span>Linux私有构建机/Mac/Win10需安装docker，Win7仅支持Coverity。
                    <a target="_blank" :href="dockerHref">具体请见>></a>
                </span>
            </div>
            <template v-for="(obj, key) in basicTabModel">
                <form-field class="head-level" v-if="key === 'languages'" :key="key" :desc="obj.desc" :required="obj.required" :label="obj.label" :is-error="errors.has(key)" :error-msg="errors.first(key)">
                    <component 
                        :is="obj.type" 
                        :atom-value="atomValue"
                        :name="key"
                        :value="atomValue[key]" 
                        :handle-change="handleUpdate" 
                        v-bind="obj" 
                        v-validate.initial="Object.assign({}, obj.rule, { required: !!obj.required })"
                    >
                    </component>
                </form-field>
            </template>
            <template v-for="prop in groupList">
                <codecc-accordion show-checkbox show-content :key="prop.id" v-if="showGroup(prop.rely, prop.id)" :label="prop.label" :desc="prop.id === 'ruleSet' ? toolsCn : ''">
                    <div slot="content" class="bk-form">
                        <template v-for="key of prop.item">
                            <form-field  v-if="atomModel[key] && rely(atomModel[key], atomValue)" :inline="atomModel[key].inline" :key="key" :desc="atomModel[key].desc" :required="atomModel[key].required" :label="atomModel[key].label" :is-error="errors.has(key)" :error-msg="errors.first(key)">
                                <component 
                                    :is="basicTabModel[key].type" 
                                    :atom-value="atomValue" 
                                    :name="key"
                                    :value="atomValue[key]" 
                                    :handle-change="handleUpdate"
                                    :placeholder="getPlaceholder(basicTabModel[key], atomValue)"
                                    v-bind="basicTabModel[key]" 
                                    v-validate.initial="Object.assign({}, basicTabModel[key].rule, { required: !!basicTabModel[key].required })"
                                    :dataList="list.content"
                                    :get-rule-set-list="getRuleSetList"
                                >
                                </component>
                            </form-field>
                        </template>
                    </div>
                </codecc-accordion>
            </template>
            <template v-for="(obj, key) in basicTabModel">
                <form-field class="head-level" :inline="obj.inline" v-if="commonItem.includes(key) && !isHidden(obj, atomValue) && rely(obj, atomValue)" :key="key" :desc="obj.desc" :required="obj.required" :label="obj.label" :is-error="errors.has(key)" :error-msg="errors.first(key)">
                    <component 
                        :is="obj.type" 
                        :atom-value="atomValue"
                        :name="key"
                        :placeholder="getPlaceholder(obj, atomValue)"
                        :value="atomValue[key]" 
                        :handle-change="handleUpdate" 
                        v-bind="obj" 
                        v-validate.initial="Object.assign({}, obj.rule, { required: !!obj.required })"
                    >
                    </component>
                </form-field>
            </template>
        </template>
    </section>
</template>

<script>
    import { atomMixin } from 'bkci-atom-components'
    import { getQueryParams } from '@/utils/util'
    import CodeccAccordion from './CodeccAccordion'
    import RuleSetSelect from './RuleSetSelect'
    
    export default {
        name: 'basic',
        mixins: [atomMixin],
        components: {
            CodeccAccordion,
            RuleSetSelect,
        },
        data () {
            return {
                languageItem: ['languages'],
                commonItem: ['goPath', 'pyVersion', 'asynchronous'],
                groupList: [
                    {
                        id: 'script',
                        label: '编译脚本',
                        rely: ['COVERITY', 'KLOCWORK', 'PINPOINT', 'CODEQL', 'CLANG', 'SPOTBUGS'],
                        item: ['scriptType', 'script']
                    }
                ],
                list: {
                    content: []
                },
                dockerHref: `${DEVOPS_SITE_URL}/console/store/atomStore/detail/atom/CodeccCheckAtomDebug`
            }
        },
        computed: {
            projectId () {
                const query = getQueryParams(location.href)
                return query && query.projectId || ''
            },
            basicTabModel () {
                const model = Object.keys(this.atomModel).reduce((model, obj) => {
                    if (this.atomModel[obj] && this.atomModel[obj].tabName === 'basic') {
                        model = Object.assign(model, { [obj]: this.atomModel[obj]})
                    }
                    return model
                }, {})
                return model
            },
            langMap () { // 获取语言是否非编译Map
                return this.atomModel.languages.list.reduce((langMap, l) => {
                    langMap[l.id] = l.compile
                    return langMap
                }, {})
            },
            hasCompileLang () { // 当前选中语言是否包含编译型语言
                const { langMap, atomValue } = this
                return atomValue.languages.some(lang => langMap[lang])
            },
            ruleList () {
                return this.list.content || []
            },
            selectRuleList () {
                return this.ruleList && this.ruleList.filter(item => this.ruleSetIds.includes(item.checkerSetId))
            },
            ruleSetIds () {
                let ruleIds = []
                for (const key in this.atomValue.languageRuleSetMap) {
                    const curLang = key.replace('_RULE', '')
                    if (this.atomValue.languages.includes(curLang)) {
                        ruleIds = ruleIds.concat(this.atomValue.languageRuleSetMap[key])
                    }
                }
                return ruleIds
            },
            toolsCn () { 
                let toolsCn = []
                if (this.atomValue.tools && this.atomValue.tools.length) {
                    toolsCn = this.atomValue.tools.map(tool => {
                        const curTool = this.atomModel.tools.list.find(item => item.id === tool)
                        return curTool && curTool.name || tool
                    })
                }
                return toolsCn.length ? `涉及工具：${toolsCn.join('、')}` : ''
            },
            envSupport () {
                return ['MACOS', 'WINDOWS'].includes(this.containerInfo.baseOS) || (this.containerInfo.dispatchType && this.containerInfo.dispatchType.buildType.indexOf('THIRD_PARTY') !== -1)
            }
        },
        watch: {
            containerInfo (value) {
                if (value.baseOS === 'WINDOWS') {
                    this.atomValue.scriptType = 'BAT'
                    this.atomModel.scriptType.default = 'BAT'
                    this.atomModel.scriptType.lang = 'bat'
                    this.atomModel.scriptType.list = [{
                        "id": "bat",
                        "value": "BAT",
                        "label": "bat"
                    }]
                    this.atomModel.script.default = "# Coverity/Klocwork将通过调用编译脚本来编译您的代码，以追踪深层次的缺陷\n# 请使用依赖的构建工具如maven/cmake等写一个编译脚本build.bat\n# 确保build.bat能够编译代码\n# cd path/to/build.bat\n# call build.bat"
                    if (this.atomValue.script === "# Coverity/Klocwork将通过调用编译脚本来编译您的代码，以追踪深层次的缺陷\n# 请使用依赖的构建工具如maven/cmake等写一个编译脚本build.sh\n# 确保build.sh能够编译代码\n# cd path/to/build.sh\n# sh build.sh") {
                        this.atomValue.script = "# Coverity/Klocwork将通过调用编译脚本来编译您的代码，以追踪深层次的缺陷\n# 请使用依赖的构建工具如maven/cmake等写一个编译脚本build.bat\n# 确保build.bat能够编译代码\n# cd path/to/build.bat\n# call build.bat"
                    }
                }
            }
        },
        async created () {
            const ruleModel = {
                'rule': { 'ruleSetRequired': true },
                'type': 'rule-set-select',
                'label': '',
                'hidden': false,
                'required': true,
                'tabName': 'basic',
                'inline': true,
                'default': [],
                'rely': {
                    'operation': 'AND',
                    'expression': [
                        {
                            'key': 'languages',
                            'value': ''
                        }
                    ]
                }
            }
            this.init()
            this.$store.dispatch('getToolMeta').then(res => {
                const ruleModelNameList = []
                this.atomModel.languages.list = res.LANG.map(item => {
                    const lang = {}
                    lang['id'] = item['langFullKey']
                    lang['name'] = item['fullName']
                    lang['compile'] = item['langType'] === 'compile'

                    const ruleModelName = item['langFullKey'] + '_RULE'
                    const ruleModelValue = { ...ruleModel, 'label': item.fullName, 'rely': { 'expression': [{ 'key': 'languages', 'value': item.langFullKey }] } }
                    ruleModelNameList.push(ruleModelName)
                    this.atomModel[ruleModelName] = ruleModelValue

                    return lang
                })
                this.groupList.unshift({ id: 'ruleSet', label: '规则集', item:ruleModelNameList })
            })
            this.$store.dispatch('getToolList').then(res => {
                this.atomModel.tools.list = res.map(item => {
                    item['id'] = item['name']
                    item['name'] = item['displayName']
                    return item
                })
            })
            if (this.atomValue && this.atomValue.languageRuleSetMap && typeof this.atomValue.languageRuleSetMap === 'object') {
                for (const key in this.atomValue.languageRuleSetMap) {
                    this.handleUpdate(key, this.atomValue.languageRuleSetMap[key])
                }
            }
        },
        methods: {
            init () {
                this.$store.dispatch('params')
                this.$store.commit('updateProjectId', this.projectId)
                this.$store.dispatch('count', { projectId: this.projectId })
                this.getRuleSetList()
            },
            async getRuleSetList () {
                try {
                    const params = {
                        pageNum: 1,
                        projectInstalled: true,
                        pageSize: 10000,
                        projectId: this.projectId
                    }
                    const res = await this.$store.dispatch('listPageable', params)
                    this.list = res
                } catch (err) {
                    console.log(err, '获取规则集失败')
                    this.$bkMessage({
                        message: '获取规则集失败',
                        theme: 'error'
                    })
                } finally {
                    this.handleTools()
                }
            },
            handleUpdate (name, value) {
                this.atomValue[name] = value
                if (name === 'languages') {
                    this.handleLangMap()
                    // this.handleTools()
                } else if (name.endsWith('_RULE')) {
                    const preValue = this.atomValue.languageRuleSetMap || {}
                    this.atomValue.languageRuleSetMap = Object.assign({}, preValue, { [name]: value })
                    this.handleTools()
                } else if (name === 'tools') {
                    //  如果是取消勾选了某些语言，需要把botRemaindTools和reportTools里面的此项工具也取消勾选
                    if (this.atomValue.botRemaindTools && this.atomValue.botRemaindTools.length ) {
                        const botToolsDiff = this.atomValue.botRemaindTools.filter(tool => !value.includes(tool))
                        botToolsDiff.forEach( (item) => { 
                            this.atomValue.botRemaindTools.splice(this.atomValue.botRemaindTools.findIndex(tool => tool === item), 1)
                        })
                    }
                    if (this.atomValue.reportTools && this.atomValue.reportTools.length) {
                        const reportToolsDiff = this.atomValue.reportTools.filter(tool => !value.includes(tool))
                        reportToolsDiff.forEach( (item) => { 
                            this.atomValue.reportTools.splice(this.atomValue.reportTools.findIndex(tool => tool === item), 1)
                        })
                    } 
                }
            },
            // 根据当前的工具判断需要展示该group, 如果是script，还要判断当前语言是否是编译型语言
            showGroup (relyTools, id) {
                if (id === 'ruleSet') {
                    return this.atomValue.languages && this.atomValue.languages.length
                }
                if (id === 'script') {
                    return this.hasCompileLang && this.atomValue.tools.find(tool => relyTools.filter(item => item === tool).length)
                }
                return true
            },
            handleTools () {
                if (this.list.content && this.list.content.length) {
                    const toolStr = this.selectRuleList.map(item => item.toolList).join()
                    const toolList = Array.from(new Set(toolStr.split(','))).filter(item => item)
                    this.handleUpdate('tools', toolList)
                }
            },
            handleLangMap () {
                for (const key in this.atomValue.languageRuleSetMap) {
                    const curLang = key.replace('_RULE', '')
                    if (!this.atomValue.languages.includes(curLang)) {
                        this.handleUpdate(key, [])
                    }
                }
            }
        }
    }
</script>

<style scoped>
    .atom-txt {
        color: #63656e;
    }
</style>
