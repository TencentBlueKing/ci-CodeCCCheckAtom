<template>
    <div class="task-select" v-bk-clickoutside="handleBlur">
        <bk-input
            :clearable="true"
            v-model="curValue"
            @focus="handleFocus"
            @keyup="handleKeyUp"
            @clear="handleClear">
        </bk-input>
        <div class="bk-options-wrapper" v-show="optionsVisible">
            <ul class="bk-options">
                <li class="bk-select-empty" v-if="optionsLoading">正在加载中...</li>
                <li class="bk-select-empty" v-else-if="!filterTaskList || !filterTaskList.length">无匹配数据</li>
                <li 
                    class="bk-option custom-option" 
                    v-show="!optionsLoading && filterTaskList && filterTaskList.length" 
                    v-for="option in filterTaskList" 
                    :key="option.taskId"
                    @click.stop="handleSelect(option)">
                    <div class="bk-option-content">
                        <span>{{option.nameCn}} (ID: {{option.taskId}})</span>
                        <i class="bk-icon icon-cog" @click.stop="handleDeleteOption(option)"></i>
                    </div>
                </li>
            </ul>
            <div class="bk-select-extension">
                <a class="bk-selector-create-item" @click="handleNewTask">
                    <i class="bk-icon icon-plus-circle" />
                    新增任务
                </a>
            </div>
        </div>
    </div>
    
</template>

<script>
    import { getQueryParams } from '@/utils/util'
    export default {
        name: 'task-select',
        props: {
            name: {
                type: String,
                default: ''
            },
            value: {
                type: [Number, String],
                default: ''
            },
            display: {
                type: String,
                default: ''
            },
            handleChange: {
                type: Function,
                default: () => () => {}
            }
        },
        data () {
            return {
                optionsVisible: false,
                optionsLoading: true,
                curValue: '',
                taskList: []
            }
        },
        computed: {
            projectId () {
                const query = getQueryParams(location.href)
                return (query && query.projectId) || ''
            },
            env () {
                const host = location.host
                const env = host.split('.').includes('dev') ? 'dev.' : host.split('.').includes('test') ? 'test.' : ''
                return env
            },
            filterTaskList () {
                return this.taskList.filter(task => `${task.nameCn} (ID: ${task.taskId})`.includes(this.curValue || ''))
            }
        },
        watch: {
            optionsVisible (value) {
                if (!value) {
                    this.$store.commit('updateExtraHeight', 130)
                } else {
                    this.$store.commit('updateExtraHeight', 250)
                }
            }
        },
        async created () {
            this.curValue = this.display || this.value
            this.$store.commit('updateProjectId', this.projectId)
        },
        methods: {
            async fetchList () {
                this.optionsLoading = true
                const res = await this.$store.dispatch('taskList', {
                    taskSource: 'bs_codecc',
                })
                this.taskList = (res && res.enableTasks) || []
                this.optionsLoading = false
            },
            handleFocus () {
                this.fetchList()
                this.optionsVisible = true
            },
            handleBlur (value, event) {
                this.optionsVisible = false
            },
            handleKeyUp (value) {
                this.$emit('input', value)
                this.handleChange(this.name, value)
                this.handleChange(this.name + 'Display', '')
            },
            handleClear () {
                this.$emit('input', '')
                this.handleChange(this.name, '')
                this.handleChange(this.name + 'Display', '')
            },
            handleSelect (option = {}) {
                this.$emit('input', option.taskId)
                this.handleChange(this.name, option.taskId)
                const displayName = `${option.nameCn} (ID: ${option.taskId})`
                this.handleChange(this.name + 'Display', displayName)
                this.curValue = displayName
                this.optionsVisible = false
            },
            handleNewTask () {
                const href = `${DEVOPS_SITE_URL}/console/codecc/${this.projectId}/task/new`
                window.open(href)
            },
            handleDeleteOption (option) {
                const href = `${DEVOPS_SITE_URL}/console/codecc/${option.projectId}/task/${option.taskId}/settings/code`
                window.open(href)
            }
        }
    }
</script>

<style scoped>
    .custom-option .icon-cog {
        display: none;
        position: absolute;
        right: 0;
        top: 3px;
        font-size: 14px;
        width: 26px;
        height: 26px;
        line-height: 26px;
        text-align: center;
    }
    .custom-option:hover .icon-cog {
        display: block;
    }
    .task-select {
        position: relative;
        width: 434px;
    }
    .bk-options-wrapper {
        position: absolute;
        width: 100%;
        border: 1px solid #dcdee5;
        line-height: 32px;
        background: #fff;
        color: #63656e;
        overflow: hidden;
    }
    .bk-options {
        max-height: 120px;
    }
    .bk-selector-create-item {
        width: 100%;
        display: inline-block;
        cursor: pointer;
    }
</style>
