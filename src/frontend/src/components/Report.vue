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
                        </form-field>

                        <form-field class="second-label" style="margin-left:120px;margin-top:-35px" v-if="key === 'botRemindRange'" :inline="true" :key="key" :desc="atomModel[key].desc" :required="atomModel[key].required" :label="atomModel[key].label">
                            <enum-input :name="key" :value="atomValue[key]" :list="atomModel[key].list" :handleChange="handleUpdate"></enum-input>
                        </form-field>

                        <form-field class="second-label" style="margin-left:120px;margin-top:-35px" v-if="key === 'reportDate'" :inline="true" :key="key" :desc="atomModel[key].desc" :required="atomModel[key].required" :label="atomModel[key].label">
                            <week-selector :name="key" :value="atomValue[key]" :handleChange="handleUpdate"></week-selector>
                        </form-field>

                        <form-field style="margin-left:190px" v-if="key === 'botRemindSeverity'" :inline="true" :key="key" :desc="atomModel[key].desc" :required="atomModel[key].required" :label="atomModel[key].label">
                            <selector :name="key" :value="atomValue[key]" :options="atomModel[key].options" :handleChange="handleUpdate"></selector>
                        </form-field>

                        <form-field class="second-label" v-if="key === 'botRemaindTools'" :inline="true" :key="key" :desc="atomModel[key].desc" :required="atomModel[key].required" :label="atomModel[key].label" :is-error="errors.has(key)" :error-msg="errors.first(key)" style="margin-left:120px">
                            <selector v-if="botRemaindTools && botRemaindTools.length" :name="key" :value="atomValue[key]" :options="botRemaindTools" :handleChange="handleUpdate" :optionsConf="atomModel[key].optionsConf"></selector>
                            <span v-else class="normal-tips">暂时只支持Coverity和Klocwork</span>
                        </form-field>

                        <form-field class="second-label" v-if="key === 'reportTime'" :inline="true" :key="key" :desc="atomModel[key].desc" :required="atomModel[key].required" :label="atomModel[key].label" :is-error="errors.has(key)" :error-msg="errors.first(key)" style="margin-left:120px">
                            <bk-time-picker format="HH:mm" :name="key" :value="atomValue[key]" placeholder="选择时间" @change="(time) => handleUpdate(key, time)"></bk-time-picker>
                        </form-field>

                        <form-field class="second-label" v-if="key === 'reportTools'" :inline="true" :key="key" :desc="atomModel[key].desc" :required="atomModel[key].required" :label="atomModel[key].label" :is-error="errors.has(key)" :error-msg="errors.first(key)" style="margin-left:120px">
                            <selector :name="key" :value="atomValue[key]" :options="reportTools" :handleChange="handleUpdate" :optionsConf="atomModel[key].optionsConf"></selector>
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
    export default {
        name: 'report',
        mixins: [atomMixin],
        components: {
            WeekSelector,
            CodeccAccordion
        },
        computed: {
            botRemaindTools () {
                const tools = JSON.parse(JSON.stringify(this.atomModel.tools && this.atomModel.tools.list))
                return tools.filter(item => this.atomValue.tools.includes(item.id) && ['COVERITY', 'KLOCWORK'].includes(item.id))
            },
            reportTools () {
                const tools = JSON.parse(JSON.stringify(this.atomModel.tools && this.atomModel.tools.list))
                return tools.filter(item => this.atomValue.tools.includes(item.id))
            }
        },
        data () {
            return {
                groupList: [
                    {
                        label: '消息通知',
                        item: ['rtxReceiverType', 'rtxReceiverList'],
                        desc: '所有工具分析结果和异常通过企业微信消息提醒实时反馈'
                    },
                    {
                        label: '群机器人通知',
                        item: ['botWebhookUrl', 'botContent', 'botRemindRange', 'botRemindSeverity', 'botRemaindTools'],
                        desc: '到企业微信群添加群机器人，复制Webhook地址到这里。'
                    },
                    {
                        label: '邮件报告',
                        item: ['emailReceiverType', 'emailReceiverList', 'emailCCReceiverList', 'instantReportStatus', 'timerEmail' ,'reportDate', 'reportTime', 'reportTools'],
                        desc: '展示项目告警的作者分布和遗留趋势等'
                    }
                ],
                customItem: ['botRemindRange', 'botRemaindTools', 'botRemindSeverity', 'reportDate', 'reportTime', 'reportTools']
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
</style>