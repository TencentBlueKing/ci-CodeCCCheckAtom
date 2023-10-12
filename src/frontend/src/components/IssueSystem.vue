<template>
    <div>
        <bk-popover v-if="issueInfo.subSystemId" placement="right" theme="light">
            <bk-button :text="true" theme="primary">{{issueInfo.subSystemCn}}</bk-button>
            <span class="refresh-link" v-if="refresh" @click="handleRefresh">{{$t('已切换授权点此刷新')}}</span>
            <div slot="content">
                <bk-link theme="primary" @click="jumpTo('tapd')">{{$t('去Tapd')}}</bk-link>
                <span class="split"></span>
                <bk-link theme="primary" @click="jumpTo('oauth')">{{$t('切换授权')}}</bk-link>
            </div>
        </bk-popover>
        <span v-else>
            <bk-button theme="primary" @click="jumpTo('oauth')">{{$t('授权OAuth')}}</bk-button>
            <span class="refresh-link" v-if="refresh" @click="handleRefresh">{{$t('已授权点此刷新')}}</span>
        </span>
    </div>
</template>

<script>
    export default {
        name: 'issue-system',
        props: {
            name: {
                type: String,
                required: true
            },
            value: [String],
            isError: {
                type: Boolean,
                default: false
            },
            handleChange: {
                type: Function,
                default: () => () => {}
            },
            issueInfo: {
                type: Object
            },
            refreshOauth: {
                type: Function
            }
        },
        components: {
        },
        data () {
            return {
                refresh: false
            }
        },
        computed: {
            restProps () {
                const { name, value, ...restProps } = this.$attrs
                return restProps
            },
        },
        created () {
        },
        methods: {
            handleUpdate(value) {
                this.handleChange(this.name, value)
            },
            jumpTo(to) {
                const list = this.issueInfo.issueSystemInfoVOList || []
                const info = list.find(item => item.system === this.issueInfo.system)
                if (info) {
                    if (to === 'tapd') {
                        window.open(info.homeUrl)
                    } else if (to === 'oauth') {
                        window.open(info.oauthUrl)
                        this.refresh = true
                    }
                }
            },
            handleRefresh() {
                this.refresh = false
                this.refreshOauth()
            }
        }
    }
</script>

<style lang="scss" scoped>
    .split {
        padding: 0 8px;
        &:after {
        content: "|";
        color: #e1ecff;
        }
    }
    .refresh-link {
        color: #3c96ff;
        font-size: 12px;
        cursor: pointer;
        padding-left: 8px;
    }
</style>