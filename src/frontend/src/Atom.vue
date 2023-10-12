<template>
    <section class="bk-form bk-form-vertical">
        <template v-for="(obj, key) in mainModel">
            <form-field
                class="head-level" 
                :inline="obj.inline" 
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
                    v-validate.initial="Object.assign({}, obj.rule, { required: !!obj.required })">
                </component>
                <a v-if="key === 'asyncTask'" :href="linkUrl" target="_blank" class="codecc-link">{{$t('前往CodeCC')}}</a>
            </form-field>
        </template>
        <div class="container-arrow" :class="{'arrow-right': atomValue.asyncTask}"></div>
        <div class="container">
            <async v-if="atomValue.asyncTask" :atom-props-model="atomModel" :atom-props-value="atomValue" :atom-props-container-info="containerInfo"></async>
            <bk-tab v-else :active.sync="active" type="unborder-card" ext-cls="codecc-tab">
                <bk-tab-panel
                    v-for="(panel, index) in panels"
                    v-bind="panel"
                    render-directive="if"
                    :key="index">
                    <span slot="label" @click="handleRedPoint(panel.name)">
                        <span>{{panel.label}}</span>
                        <i v-if="tabRedTips[panel.name]" class="red-point"></i>
                    </span>
                    <component :is="panel.name" :atom-props-model="atomModel" :atom-props-value="atomValue" :atom-props-container-info="containerInfo"></component>
                </bk-tab-panel>
                <!-- <template slot="setting">
                    <a :href="linkUrl" target="_blank" class="codecc-link">前往CodeCC</a>
                </template> -->
            </bk-tab>
        </div>
    </section>
</template>

<script>
    import { atomMixin }from 'bkci-atom-components'
    import { getQueryParams } from '@/utils/util'
    import Basic from '@/components/Basic'
    import Report from '@/components/Report'
    import Scan from '@/components/Scan'
    import Issue from '@/components/Issue'
    import Shield from '@/components/Shield'
    import Async from '@/components/Async'
    import DEPLOY_ENV from '@/constants/env';

    export default {
        name: 'atom',
        mixins: [atomMixin],
        components: {
            Basic,
            Report,
            Scan,
            Issue,
            Shield,
            Async
        },
        data () {
            const panels = (DEPLOY_ENV === 'tencent' 
            ? [
                { name: 'basic', label: this.$t('基础设置') },
                { name: 'report', label: this.$t('通知报告') },
                { name: 'issue', label: this.$t('问题提单') },
                { name: 'scan', label: this.$t('扫描配置') },
                { name: 'shield', label: this.$t('路径屏蔽') }
              ]
            : [
                { name: 'basic', label: this.$t('基础设置') },
                { name: 'scan', label: this.$t('扫描配置') },
                { name: 'shield', label: this.$t('路径屏蔽') }
              ]
            )
            return {
                panels,
                active: 'basic',
                tabRedTips: {
                    'issue': false
                }
            }
        },
        computed: {
            linkUrl () {
                const query = getQueryParams(location.href)
                const { host, protocol } = location;
                const projectId = query && query.projectId || ''
                return `${protocol}//${host}/console/codecc/${projectId}/task/list`
            },
            mainModel () {
                const model = Object.keys(this.atomModel).reduce((model, obj) => {
                    if (this.atomModel[obj] && this.atomModel[obj].tabName === 'main') {
                        model = Object.assign(model, { [obj]: this.atomModel[obj]})
                    }
                    return model
                }, {})
                return model
            }
        },
        watch: {
            'errors.items': {
                handler: function (newVal, oldVal) {
                    this.setAtomIsError(!!newVal.length)
                },
                deep: true,
                immediate: true
            },
            active: function (newValue, oldValue) {
                this.tabRedTips['issue'] = !window.localStorage.getItem('tapd-20210628')
            }
        },
        created () {
            // 兼容老数据，没有的值的话，默认为false
            if (this.atomValue.asyncTask === undefined) {
                this.$set(this.atomValue, 'asyncTask', false)
            }
        },
        methods: {
            handleUpdate (name, value) {
                this.atomValue[name] = value
            },
            handleRedPoint (name) {
                if (name === 'issue') {
                    window.localStorage.setItem('tapd-20210628', '1')
                }
            }
        }
    }
</script>

<style lang="scss">
    .bk-options .bk-option-content .bk-option-icon {
        right: 5px !important;
    }
    .codecc-link {
        padding-right: 12px;
        text-decoration: none;
        color: #3a84ff;
        cursor: pointer;
        position: absolute;
        top: 1px;
        right: 0;
    }
    .codecc-tab {
        .bk-tab-section {
            padding: 10px 20px;
        }
        .bk-tab-label-list .active {
            border: #dcdee5;
        }
        .head-level .bk-label {
            font-weight: bold;
        }
        .staff-selector .tag-info {
            line-height: 18px;
        }
    }
    ::-webkit-scrollbar {
        width: 0;
        height: 9px;
        background-color: white;
    }
    ::-webkit-scrollbar-thumb {
        height: 8px;
        border-radius: 20px;
        background-color: #a5a5a5;
    }
    ul {
        margin: 0px;
        padding: 0px;
    }
    .atom-txt {
        color: #63656e;
        padding-bottom: 10px;
        .bk-icon {
            font-size: 14px;
            color: #ff9c01;
            padding-right: 5px;
        }
    }
    .task-content {
        padding: 15px;
    }
    .container-arrow {
        border-bottom: 8px solid #dcdee5;
        border-left: 8px solid transparent;
        border-right: 8px solid transparent;
        width: 16px;
        position: relative;
        left: 145px;
        &.arrow-right {
            left: 220px;
        }
        &:after {
            content: '';
            border-bottom: 7px solid #fff;
            border-left: 7px solid transparent;
            border-right: 7px solid transparent;
            position: absolute;
            top: 2px;
            left: -7px;
        }
    }
    .container {
       border: 1px solid #dcdee5;
    }
    .codecc-tab {
        .bk-tab-header {
            pointer-events: auto;
        }
    }
    .red-point {
        width: 6px;
        height: 6px;
        background: #f44343;
        border-radius: 3px;
        display: inline-block;
        margin-bottom: 7px;
        margin-left: -3px;
    }
    .form-field-icon, .bk-tooltip {
        pointer-events: auto;
    }
</style>
 