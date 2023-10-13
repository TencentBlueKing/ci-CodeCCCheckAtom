<template>
    <section class="bk-form">
        <template>
            <div class="atom-txt" v-if="envSupport">
                <span>{{$t('Linux私有构建机/Mac/Win10需安装docker，Win7仅支持Coverity。')}}
                    <a target="_blank" :href="handleCovHref()">{{$t('了解更多')}}>></a>
                </span>
            </div>
            <div class="atom-txt">
                <span>{{$t('前置的拉取代码插件请勿勾选Pre-Merge，避免影响增量扫描等功能。')}}
                    <a v-if="isInnerSite" target="_blank" :href="preMergeUrl">{{$t('了解更多')}}>></a>
                </span>
            </div>
            <template v-for="(obj, key) in basicTabModel">
                <form-field class="head-level"
                    v-if="showBasic(key)" 
                    :key="key" 
                    :desc="obj.desc" 
                    :inline="obj.inline" 
                    :required="obj.required" 
                    :label="obj.label" 
                    :is-error="errors.has(key)" 
                    :error-msg="errors.first(key)">
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
                <codecc-accordion 
                    show-checkbox 
                    show-content 
                    :key="prop.id" 
                    v-if="showGroup(prop.rely, prop.id)" 
                    :label="prop.label" 
                    :desc="prop.id === 'ruleSet' || prop.id === 'ruleSetEnv' ? toolsCn : ''">
                    <div slot="content" class="bk-form">
                        <template v-for="key of prop.item">
                            <form-field  
                                v-if="atomModel[key] && rely(atomModel[key], atomValue)" 
                                :inline="atomModel[key].inline" 
                                :key="key" 
                                :desc="atomModel[key].desc" 
                                :required="atomModel[key].required" 
                                :label="atomModel[key].label" 
                                :is-error="errors.has(key)" 
                                :error-msg="errors.first(key)">
                                <component 
                                    :is="basicTabModel[key].type" 
                                    :atom-value="atomValue" 
                                    :name="key"
                                    :value="atomValue[key]" 
                                    :handle-change="handleUpdate"
                                    :placeholder="getPlaceholder(basicTabModel[key], atomValue)"
                                    v-bind="basicTabModel[key]" 
                                    v-validate.initial="Object.assign({}, basicTabModel[key].rule, { required: !!basicTabModel[key].required })"
                                    :dataList="getDataList(prop.id)"
                                    :get-rule-set-list="getRuleSetList"
                                >
                                </component>
                            </form-field>
                        </template>
                    </div>
                </codecc-accordion>
            </template>
            <template v-for="(obj, key) in basicTabModel">
                <form-field class="head-level" 
                    :inline="obj.inline" 
                    v-if="commonItem.includes(key) && !isHidden(obj, atomValue) && rely(obj, atomValue)" 
                    :key="key" 
                    :desc="obj.desc" 
                    :required="obj.required" 
                    :label="obj.label" 
                    :is-error="errors.has(key)" 
                    :error-msg="errors.first(key)">
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
    import RuleSetEnvSelect from './RuleSetEnvSelect'
    import ItemEdit from './ItemEdit'
    import DEPLOY_ENV from '@/constants/env';
    
    export default {
        name: 'basic',
        mixins: [atomMixin],
        components: {
            CodeccAccordion,
            RuleSetSelect,
            RuleSetEnvSelect,
            ItemEdit
        },
        data () {
            return {
                languageItem: ['languages'],
                commonItem: ['goPath', 'pyVersion', 'asynchronous', 'multiPipelineMark', 'bkcheckDebug', 'bkcheckCustomMacros'],
                groupList: [
                    {
                        id: 'script',
                        label: this.$t('编译脚本'),
                        rely: ['COVERITY', 'KLOCWORK', 'PINPOINT', 'CODEQL', 'CLANG', 'SPOTBUGS', 'CLANGWARNING', 'PVS', 'ANDROID-LINT', 'APICHECK', 'CODEQL-WX', 'PECKERDEFECTSCAN', 'WECHECK'],
                        item: ['scriptType', 'script']
                    },
                    {
                        id: 'ruleSetEnv',
                        label: '',
                        rely: [],
                        item: ['checkerSetEnvType']
                    }
                ],
                list: {
                    content: []
                },
                checkerSetEnvData: {},
                isInnerSite: DEPLOY_ENV === 'tencent',
                preMergeUrl: window.IWIKI_SITE_URL + '/p/345196930'
            }
        },
        computed: {
            projectId () {
                const query = getQueryParams(location.href)
                return query && query.projectId || ''
            },
            pipelineId () {
                const query = getQueryParams(location.href)
                return query && query.pipelineId || ''
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
                return toolsCn.length ? this.$t('涉及工具x', [toolsCn.join('、')]) : ''
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
                    this.atomModel.script.default = ""
                    if (this.atomValue.script === this.$t("# Coverity/Klocwork将通过调用编译脚本来编译您的代码，以追踪深层次的缺陷\n# 请使用依赖的构建工具如maven/cmake等写一个编译脚本build.sh\n# 确保build.sh能够编译代码\n# cd path/to/build.sh\n# sh build.sh")) {
                        this.atomValue.script = ""
                    }
                } else {
                    this.atomModel.script.default = this.$t("# Coverity/Klocwork将通过调用编译脚本来编译您的代码，以追踪深层次的缺陷\n# 请使用依赖的构建工具如maven/cmake等写一个编译脚本build.sh\n# 确保build.sh能够编译代码\n# cd path/to/build.sh\n# sh build.sh")
                }
            },
            'atomValue.checkerSetType' (value) {
                if (value === 'epcScan') {
                    this.handleUpdate('scanTestSource', true)
                }
                if (value !== 'normal') {
                    this.handleCustomTools()
                }
            },
            'atomValue.checkerSetEnvType' (value) {
                if (this.atomValue.checkerSetType !== 'normal') {
                    this.handleCustomTools()
                }
            },
            'atomValue.beAutoLang' (value) {
                if (value) {
                    if (this.atomValue.checkerSetType === 'normal') {
                        this.atomValue.checkerSetType = 'openScan'
                    }
                    const checkerSet = this.atomModel.checkerSetType.list.find(item => item.value === 'normal') || {}
                    checkerSet.disabled = true
                } else {
                    const checkerSet = this.atomModel.checkerSetType.list.find(item => item.value === 'normal') || {}
                    checkerSet.disabled = false
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
                this.groupList.unshift({ id: 'ruleSet', label: '', item:ruleModelNameList })
            })
            this.$store.dispatch('getToolList').then(res => {
                this.atomModel.tools.list = res.map(item => {
                    item['id'] = item['name']
                    item['name'] = item['displayName']
                    return item
                })
            })
            this.$store.dispatch('getOpenScanAndPreProdCheckerSet').then(res => {
                this.checkerSetEnvData = res
                if (this.atomValue.checkerSetType !== 'normal') {
                    this.handleCustomTools()
                }
            })
            if (this.atomValue && this.atomValue.languageRuleSetMap && typeof this.atomValue.languageRuleSetMap === 'object') {
                for (const key in this.atomValue.languageRuleSetMap) {
                    this.handleUpdate(key, this.atomValue.languageRuleSetMap[key])
                }
            }
            // 自动识别语言时从后台获取最新工具信息
            const { multiPipelineMark } = this.atomValue
            const lastAnalyzeTool = await this.$store.dispatch('getLastAnalyzeTool', { multiPipelineMark }) || []
            if (this.atomValue.beAutoLang) {
                this.handleUpdate('tools', lastAnalyzeTool)
            }
        },
        methods: {
            init () {
                this.$store.dispatch('params')
                this.$store.commit('updateProjectId', this.projectId)
                this.$store.commit('updatePipelineId', this.pipelineId)
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
                        message: this.$t('获取规则集失败'),
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
                    this.handleCustomTools()
                    // this.handleTools()
                } else if (name.endsWith('_RULE')) {
                    const preValue = this.atomValue.languageRuleSetMap || {}
                    this.atomValue.languageRuleSetMap = Object.assign({}, preValue, { [name]: value })
                    this.handleTools()
                } else if (name === 'tools') {
                    //  如果是取消勾选了某些语言，需要把botRemaindTools和reportTools里面的此项工具也取消勾选
                    if (this.atomValue.botRemaindTools && this.atomValue.botRemaindTools.length ) {
                        const botToolsDiff = this.atomValue.botRemaindTools.filter(tool => !value.includes(tool) && tool !== 'ALL')
                        botToolsDiff.forEach( (item) => { 
                            this.atomValue.botRemaindTools.splice(this.atomValue.botRemaindTools.findIndex(tool => tool === item), 1)
                        })
                    }
                    if (this.atomValue.reportTools && this.atomValue.reportTools.length) {
                        const reportToolsDiff = this.atomValue.reportTools.filter(tool => !value.includes(tool) && tool !== 'ALL')
                        reportToolsDiff.forEach( (item) => { 
                            this.atomValue.reportTools.splice(this.atomValue.reportTools.findIndex(tool => tool === item), 1)
                        })
                    } 
                }
            },
            // 根据当前的工具判断需要展示该group, 如果是script，还要判断当前语言是否是编译型语言
            showGroup (relyTools, id) {
                if (id === 'ruleSetEnv') {
                    return this.atomValue.checkerSetType !== 'normal'
                } else if (this.atomValue.checkerSetType !== 'normal') {
                    return false
                } else if (id === 'ruleSet') {
                    return this.atomValue.languages && this.atomValue.languages.length
                } else if (id === 'script') {
                    if (Object.keys(this.atomValue).find(item => item.includes('_TOOL'))) { // 新的脚本框判断
                        return this.handleShowScript()
                    }
                    return this.hasCompileLang && this.atomValue.tools.find(tool => relyTools.filter(item => item === tool).length)
                }
                return true
            },
            showBasic (key) {
                if (key === 'checkerSetType' || key === 'beAutoLang' || (key === 'languages' && !this.atomValue.beAutoLang)) {
                    return true
                }
                return false
            },
            handleShowScript () {
                let hasScript = false
                this.atomValue.languages.forEach(lang => {
                    const toolKey = lang + '_TOOL'
                    const relyTools = ['COVERITY', 'KLOCWORK', 'PINPOINT', 'CODEQL', 'CLANG', 'SPOTBUGS', 'CLANGWARNING', 'PVS', 'ANDROID-LINT', 'APICHECK', 'CODEQL-WX', 'PECKERDEFECTSCAN', 'WECHECK']
                    if (this.langMap[lang] && this.atomValue[toolKey]) {
                        this.atomValue[toolKey].forEach(ruleSet => {
                            if (ruleSet.toolList.find(tool => relyTools.filter(item => item === tool).length)) {
                                hasScript = true
                            }
                        })
                    }
                })
                return hasScript
            },
            handleTools () {
                if (!this.atomValue.beAutoLang && this.ruleList.length && this.selectRuleList.length) {
                    const toolStr = this.selectRuleList.map(item => item.toolList).join()
                    const toolList = Array.from(new Set(toolStr.split(','))).filter(item => item)
                    if (this.atomValue.checkerSetType !== 'normal') { // 非自主配置规则集
                        this.handleCustomTools()
                    } else {
                        this.handleUpdate('tools', toolList)
                    }
                }
            },
            handleLangMap () {
                for (const key in this.atomValue.languageRuleSetMap) {
                    const curLang = key.replace('_RULE', '')
                    if (!this.atomValue.languages.includes(curLang)) {
                        this.handleUpdate(key, [])
                    }
                }
            },
            handleCustomTools () {
                const { checkerSetEnvData } = this
                const { checkerSetType, checkerSetEnvType, languages, beAutoLang } = this.atomValue
                if (beAutoLang) return
                const checkerSetEnvKey = `${checkerSetEnvType}${checkerSetType}`.toLocaleLowerCase()
                const key = Object.keys(checkerSetEnvData).find(item => item.toLocaleLowerCase() === checkerSetEnvKey)
                const toolMap = checkerSetEnvData[key]
                if (toolMap) {
                    let lists = []
                    languages.forEach(lang => {
                        lists = lists.concat(toolMap[lang] || [])
                    })
                    let toolList = []
                    lists.forEach(item => {
                        toolList = toolList.concat(item.toolList || [])
                    })
                    toolList = Array.from(new Set(toolList))
                    if (checkerSetType && checkerSetType !== 'normal') {
                        this.handleUpdate('tools', toolList)
                    }
                }
            },
            getDataList (id) {
                if (id === 'ruleSet') {
                    return this.ruleList
                } else if (id === 'ruleSetEnv') {
                    const { checkerSetEnvData } = this
                    const checkerSetType = this.atomValue.checkerSetType
                    if (typeof checkerSetEnvData !== 'object') {
                        return []
                    }
                    const preProdKey = `preProd${checkerSetType}TimeGap`.toLocaleLowerCase()
                    const prodKey = `prod${checkerSetType}TimeGap`.toLocaleLowerCase()
                    const preProdTimeKey = Object.keys(checkerSetEnvData).find(item => item.toLocaleLowerCase() === preProdKey)
                    const prodTimeKey = Object.keys(checkerSetEnvData).find(item => item.toLocaleLowerCase() === prodKey)
                    const preProdStartTime = this.timetrans(checkerSetEnvData[preProdTimeKey] && checkerSetEnvData[preProdTimeKey].startTime)
                    const preProdEndTime = this.timetrans(checkerSetEnvData[preProdTimeKey] && checkerSetEnvData[preProdTimeKey].endTime)
                    const ProdTime = this.timetrans(checkerSetEnvData[prodTimeKey] && checkerSetEnvData[prodTimeKey].startTime)
                    const list = [
                                { id: 'preProd', name: this.$t('预发布版x发布，y转正式版', [preProdStartTime, preProdEndTime]) },
                                { id: 'prod', name: this.$t('正式版x发布', [ProdTime]) }
                            ]
                    return list
                }
            },
            timetrans (time) {
                if (!time) {
                    return '--'
                }
                const date = new Date(Number(time));
                const Y = date.getFullYear() + '-';
                const M = (date.getMonth() + 1 < 10 ? '0'+(date.getMonth() + 1) : date.getMonth() + 1) + '-';
                const D = (date.getDate() < 10 ? '0' + (date.getDate()) : date.getDate()) + ' ';
                return `${Y}${M}${D}`;
            },
            handleCovHref () {
                return `${window.DEVOPS_SITE_URL}/console/store/atomStore/detail/atom/CodeccCheckAtomDebug`
            }
        }
    }
</script>

<style scoped>
    .atom-txt {
        color: #63656e;
    }
    .vuex-txt .desc {
        /* padding: 10px 0 10px 120px; */
        color: #999;
    }
    :deep() .bkdevops-radio {
        margin-right: 24px;
    }
</style>
