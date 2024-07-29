<template>
    <section>
        <template v-for="(prop, index) in groupList">
            <codecc-accordion show-checkbox show-content :key="index" :label="prop.label" :labelDesc="prop.desc">
                <div slot="content" class="bk-form bk-form-vertical">
                    <template v-for="key of prop.item">
                        <form-field :style="atomModel[key].innerTab ? 'margin-left:120px' : ''" v-if="!atomModel[key].hidden && rely(atomModel[key], atomValue) && !customItem.includes(key)" :key="key" :inline="atomModel[key].inline" :desc="atomModel[key].desc" :required="atomModel[key].required" :label="atomModel[key].label" :is-error="errors.has(key)" :error-msg="errors.first(key)">
                            <component 
                                :is="atomModel[key].type" 
                                :atom-value="atomValue" 
                                :name="key"
                                v-validate.initial="Object.assign({}, atomModel[key].rule, { required: !!atomModel[key].required })"
                                :value="atomValue[key]" 
                                :handle-change="handleUpdate" 
                                v-bind="atomModel[key]" 
                                :placeholder="getPlaceholder(atomModel[key], atomValue)">
                            </component>
                            <label class="normal-tips" v-if="(key === 'rtxReceiverType' || key === 'emailReceiverType') && atomValue[key] === '4'">{{$t('暂不包含重复率工具重复文件的相关作者')}}</label>
                        </form-field>

                        <form-field class="second-label" style="margin-left:120px;margin-top:-35px" v-if="key === 'botRemindRange'" :inline="true" :key="key" :desc="atomModel[key].desc" :required="atomModel[key].required" :label="atomModel[key].label">
                            <enum-input :name="key" :value="atomValue[key]" :list="atomModel[key].list" :handleChange="handleUpdate"></enum-input>
                        </form-field>

                        <form-field class="second-label" style="margin-left:120px;margin-top:-35px" v-if="key === 'reportDate'" :inline="true" :key="key" :desc="atomModel[key].desc" :required="atomModel[key].required" :label="atomModel[key].label">
                            <week-selector :name="key" :value="atomValue[key]" :handleChange="handleUpdate"></week-selector>
                        </form-field>

                        <form-field style="margin-left:120px;margin-top:-35px" class="second-label timer-report" v-if="key === 'botRemindSeverity'" :inline="true" :key="key" :desc="atomModel[key].desc" :required="atomModel[key].required" :label="atomModel[key].label">
                            <selector :name="key" :value="atomValue[key]" :options="atomModel[key].options" :handleChange="handleUpdate"></selector>
                        </form-field>

                        <form-field class="second-label timer-report" v-if="key === 'botRemaindTools'" :inline="true" :key="key" :desc="atomModel[key].desc" :required="atomModel[key].required" :label="atomModel[key].label" :is-error="errors.has(key)" :error-msg="errors.first(key)" style="margin-left:120px">
                            <selector class="bot-tool-selector" :name="key" :value="atomValue[key]" :options="botRemaindTools" :handleChange="handleUpdate" :optionsConf="atomModel[key].optionsConf"></selector>
                        </form-field>

                        <form-field class="second-label" v-if="key === 'reportTime'" :inline="true" :key="key" :desc="atomModel[key].desc" :required="atomModel[key].required" :label="atomModel[key].label" :is-error="errors.has(key)" :error-msg="errors.first(key)" style="margin-left:120px">
                            <bk-time-picker format="HH:mm" :name="key" :value="atomValue[key]" placeholder="选择时间" @change="(time) => handleUpdate(key, time)"></bk-time-picker>
                        </form-field>

                        <form-field class="second-label timer-report" v-if="key === 'reportTools'" :inline="true" :key="key" :desc="atomModel[key].desc" :required="atomModel[key].required" :label="atomModel[key].label" :is-error="errors.has(key)" :error-msg="errors.first(key)" style="margin-left:120px">
                            <selector class="report-tool-selector" :name="key" :value="atomValue[key]" :options="reportTools" :handleChange="handleUpdate" :optionsConf="atomModel[key].optionsConf"></selector>
                        </form-field>
                    </template>
                </div>
            </codecc-accordion>
        </template>
    </section>
</template>

<script>
    import { atomMixin }from 'bkci-atom-components'
    import WeekSelector from './WeekSelector'
    import CodeccAccordion from './CodeccAccordion'
    import DEPLOY_ENV from '@/constants/env';

    export default {
        name: 'report',
        mixins: [atomMixin],
        components: {
            WeekSelector,
            CodeccAccordion
        },
        computed: {
            botRemaindTools () {
                let tools = JSON.parse(JSON.stringify(this.atomModel.tools && this.atomModel.tools.list))
                tools = tools.filter(item => this.atomValue.tools.includes(item.id) && !['CLOC', 'STAT', 'SCC'].includes(item.id))
                tools.unshift({id: 'ALL', name: this.$t('所有工具')})
                return tools
            },
            reportTools () {
                let tools = JSON.parse(JSON.stringify(this.atomModel.tools && this.atomModel.tools.list))
                tools = tools.filter(item => this.atomValue.tools.includes(item.id))
                tools.unshift({id: 'ALL', name: this.$t('所有工具')})
                return tools
            }
        },
        data () {
            const pathUrl = DEPLOY_ENV === 'tencent' ? `\n<a href='${window.IWIKI_SITE_URL}/p/79242724' target='_blank' style='color: #3a84ff'>${this.$t('了解更多')}>></a>` : ''

            return {
                groupList: [
                    {
                        label: this.$t('消息通知'),
                        item: ['rtxReceiverType', 'rtxReceiverList'],
                        desc: this.$t('所有工具分析结果和异常通过企业微信消息提醒实时反馈')
                    },
                    {
                        label: this.$t('群机器人通知'),
                        item: ['botWebhookUrl', 'botContent', 'botRemindSeverity', 'botRemaindTools'],
                        desc: this.$t('到企业微信群添加群机器人，复制Webhook地址到这里。更多详细说明') + pathUrl
                    },
                    {
                        label: this.$t('邮件报告'),
                        item: ['emailReceiverType', 'emailReceiverList', 'emailCCReceiverList', 'instantReportStatus', 'timerEmail' ,'reportDate', 'reportTime', 'reportTools'],
                        desc: this.$t('展示项目问题的作者分布和遗留趋势等')
                    }
                ],
                customItem: ['botRemaindTools', 'botRemindSeverity', 'reportDate', 'reportTime', 'reportTools']
            }
        },
        methods: {
            handleUpdate (name, value) {
                this.atomValue[name] = value
            }
        }
    }
</script>

<style lang="scss">
    .normal-tips {
        font-size: 12px;
        color: #666;
    }
    .remote-atom .second-label .bk-label {
        width: 70px !important;
    }
    .staff-selector {
        width: 402px;
    }
    .timer-report .bk-select-name {
        width: 330px;
    }
    .bot-tool-selector, .report-tool-selector {
        width: 332px;
    }
</style>