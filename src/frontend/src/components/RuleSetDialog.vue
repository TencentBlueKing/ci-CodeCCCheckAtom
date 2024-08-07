<template>
    <bk-dialog v-model="visiable"
        ext-cls="install-more-dialog"
        :fullscreen="true"
        :theme="'primary'"
        :close-icon="false"
        :show-footer="false">
        <div class="main-content" v-bkloading="{ isLoading: loading, opacity: 0.3 }">
            <div class="info-header">
                <span>{{$t('选择规则集')}}<i class="bk-icon icon-refresh checkerset-fresh" :class="fetchingList ? 'spin-icon' : ''" @click="refresh" /></span>
                <div class="handle-option">
                    <bk-select class="search-select" v-model="language" multiple style="width: 120px;" :placeholder="$t('请选择语言')">
                        <bk-option v-for="option in codeLangs"
                            :key="option.displayName"
                            :id="option.displayName"
                            :name="option.displayName">
                        </bk-option>
                    </bk-select>
                    <bk-input
                        class="search-input"
                        :placeholder="$t('快速搜索')"
                        :clearable="true"
                        :right-icon="'bk-icon icon-search'"
                        v-model="keyWord"
                        @input="handleClear"
                        @enter="handleKeyWordSearch">
                    </bk-input>
                    <bk-popconfirm always
                        v-if="showTipsConfirm"
                        :confirm-text="$t('我知道了')"
                        cancel-text=""
                        :delay="500"
                        :confirm-button-is-text="false" 
                        @confirm="handleTipsConfirm">
                        <div slot="content">
                            <div>{{$t('在此处关闭规则集弹框')}}</div>
                        </div>
                        <i class="bk-icon icon-close" @click="closeDialog" />
                    </bk-popconfirm>
                    <i v-else class="bk-icon icon-close" @click="closeDialog" />
                </div>
            </div>
            <bk-tab class="checkerset-tab" size="small" ref="tab" :active.sync="classifyCode" type="unborder-card">
                <bk-tab-panel
                    class="checkerset-panel"
                    ref="checkersetPanel"
                    v-for="classify in classifyCodeList"
                    :key="classify.enName"
                    :name="classify.enName"
                    :label="classify.cnName"
                    render-directive="if">
                    <section ref="checkersetList">
                        <div :class="['info-card', { 'disabled': checkerSet.codeLangList.length > 1 || !checkerSet.codeLangList.includes(curLang), 'selected': checkIsSelected(checkerSet.checkerSetId) }]"
                            v-for="(checkerSet, index) in checkerSetList"
                            :key="index">
                            <div :class="['checkerset-icon', getIconColorClass(checkerSet.checkerSetId)]">{{(checkerSet.checkerSetName || '')[0]}}</div>
                            <div class="info-content">
                                <p class="checkerset-main">
                                    <span class="name" :title="checkerSet.checkerSetName">{{checkerSet.checkerSetName}}</span>
                                    <span v-if="['DEFAULT', 'RECOMMEND'].includes(checkerSet.checkerSetSource)"
                                        :class="['use-mark', { 'preferred': checkerSet.checkerSetSource === 'DEFAULT', 'recommend': checkerSet.checkerSetSource === 'RECOMMEND' }]"
                                    >{{checkerSet.checkerSetSource === 'DEFAULT' ? $t('精选') : $t('推荐')}}</span>
                                    <span class="language" :title="getCodeLang(checkerSet.codeLang)">{{getCodeLang(checkerSet.codeLang)}}</span>
                                </p>
                                <p class="checkerset-desc" :title="checkerSet.description">{{checkerSet.description || $t('暂无描述')}}</p>
                                <p class="other-msg">
                                    <span>{{$t('由x发布',{ name: checkerSet.creator })}}</span>
                                    <span>{{$t('共x条规则', { sum: checkerSet.checkerCount || 0 })}}</span>
                                </p>
                            </div>
                            <div class="info-operate" 
                                @mouseenter="currentHoverItem = index"
                                @mouseleave="currentHoverItem = -1">
                                <bk-button
                                    size="small"
                                    class="handle-btn"
                                    v-bk-tooltips="getToolTips(checkerSet.codeLangList.length > 1, !checkerSet.codeLangList.includes(curLang))"
                                    :class="[checkerSet.codeLangList.length > 1 || !checkerSet.codeLangList.includes(curLang) ? 'disable-btn': 'enable-btn']"
                                    :theme="classifyCode === 'store' && !checkerSet.projectInstalled ? 'primary' : 'default'"
                                    @click="handleOption(checkerSet.codeLangList.length > 1 || !checkerSet.codeLangList.includes(curLang), classifyCode === 'store' && !checkerSet.projectInstalled, checkerSet, checkIsSelected(checkerSet.checkerSetId))"
                                >{{ getSelectText(checkerSet, index) }}</bk-button>
                            </div>
                        </div>
                    </section>
                    <div v-if="!checkerSetList.length">
                        <div class="codecc-table-empty-text">
                            <img src="../images/empty.png" class="empty-img">
                            <div>{{$t('暂无数据')}}</div>
                        </div>
                    </div>
                </bk-tab-panel>
                <template slot="setting">
                <a :href="linkUrl" target="_blank" class="codecc-link">{{$t('创建规则集')}}</a>
            </template>
            </bk-tab>
        </div>
    </bk-dialog>
</template>

<script>
    import { mapState } from 'vuex'
    import { getQueryParams } from '@/utils/util'

    export default {
        props: {
            visiable: {
                type: Boolean,
                default: false
            },
            curLang: {
                type: String,
                default: ''
            },
            defaultLang: {
                type: Array,
                default: () => ([])
            },
            selectedList: {
                type: Array,
                default: () => ([])
            },
            handleSelect: Function,
            getRuleSetList: Function
        },
        data () {
            return {
                fetchingList: false,
                loading: false,
                loadEnd: false,
                pageChange: false,
                isLoadingMore: false,
                isOpen: false,
                currentHoverItem: -1,
                keyWord: '',
                language: [],
                params: {
                    quickSearch: '',
                    checkerSetCategory: [],
                    checkerSetLanguage: [],
                    pageNum: 1,
                    pageSize: 10000
                },
                classifyCode: 'all',
                checkerSetList: [],
                showTipsConfirm: false
            }
        },
        computed: {
            ...mapState([
                'toolMeta',
                'categoryList',
                'codeLangs',
                'checkerSetLanguage'
            ]),
            projectId () {
                const query = getQueryParams(location.href)
                return query && query.projectId || ''
            },
            renderList () {
                let target = []
                if (this.classifyCode === 'all') {
                    target = [...this.checkerSetList]
                } else {
                    target = this.checkerSetList.filter(item => item.catagories.some(val => val.enName === this.classifyCode))
                }
                return target || []
            },
            linkUrl () {
                const { host, protocol } = location;
                return `${protocol}//${host}/console/codecc/${this.projectId}/checkerset/list#new`
            },
            classifyCodeList () {
                if (this.categoryList.length) {
                    return [{ cnName: this.$t('所有'), enName: 'all' }, ...this.categoryList, { cnName: this.$t('研发商店'), enName: 'store' }]
                }
                return []
            }
        },
        watch: {
            visiable (newVal) {
                if (newVal) {
                    this.isOpen = true
                    this.keyWord = ''
                    this.language = []
                    this.params = {
                        quickSearch: '',
                        checkerSetCategory: [],
                        checkerSetLanguage: [],
                        pageNum: 1,
                        pageSize: 10000
                    }
                    this.classifyCode = 'all'
                    // 兼容语言
                    if (this.codeLangs.findIndex(val => val.displayName === this.defaultLang[0]) < 0) {
                        this.language = []
                        this.params.checkerSetLanguage = []
                    } else {
                        this.language = this.defaultLang
                        this.params.checkerSetLanguage = this.defaultLang
                    }
                    this.requestList(true)
                    if (window.localStorage.getItem('ruleSetConfirm') !== '1') {
                        this.showTipsConfirm = true
                    }
                    // this.addScrollLoadMore()
                } else {
                    this.classifyCode = ''
                    this.showTipsConfirm = false
                }
            },
            classifyCode (newVal) {
                if (this.visiable && !this.isOpen) {
                    this.removeScrollLoadMore()
                    this.params.pageNum = 1
                    this.params.checkerSetCategory = ['all', 'store'].includes(newVal) ? [] : [newVal]
                    this.requestList(true)
                    // this.addScrollLoadMore()
                }
            },
            language (newVal) {
                if (!this.isOpen) {
                    this.pageChange = false
                    this.params.checkerSetLanguage = newVal
                    this.resetScroll()
                    this.requestList(false, this.params)
                }
            }
        },
        mounted () {
            // this.addScrollLoadMore()
        },
        beforeDestroy () {
            this.removeScrollLoadMore()
        },
        methods: {
            closeDialog () {
                this.$emit('update:visiable', false)
            },
            async requestList (isInit, params = this.params) {
                this.loading = true
                this.isLoadingMore = true
                params.projectInstalled = this.classifyCode !== 'store' ? true : undefined
                params.projectId = this.projectId
                let dispatchUrl = 'otherList'
                if (this.classifyCode !== 'store') {
                    dispatchUrl = 'listPageable'
                    params.keyWord = params.quickSearch
                }
                const res = await this.$store.dispatch(dispatchUrl, params).finally(() => {
                    this.loading = false
                    this.isOpen = false
                })
                // this.checkerSetList = this.pageChange ? this.checkerSetList.concat(res.content) : res.content
                const list = res.content || []
                this.checkerSetList = list.sort((a, b) => this.selectedList.some(item => item.checkerSetId === b.checkerSetId) - this.selectedList.some(item => item.checkerSetId === a.checkerSetId))
                this.loadEnd = res.last
                this.pageChange = false
                this.isLoadingMore = false
            },
            checkIsSelected (checkerSetId) {
                return this.selectedList.some(item => item.checkerSetId === checkerSetId)
            },
            // 点击规则集名称,跳转到规则详情页
            checkerSetNameClick (checkerSet) {
                const version = checkerSet.version
                const checkerSetId = checkerSet.checkerSetId
                const href = `${window.DEVOPS_SITE_URL}/console/codecc/${this.projectId}/checkerset/${checkerSetId}/${version}/manage`
                window.open(href, '_blank')
            },
            getSelectText (checkerSet, index) {
                let txt = ''
                if (this.classifyCode === 'store' && !checkerSet.projectInstalled) {
                    txt = this.$t('安装')
                } else {
                    txt = this.checkIsSelected(checkerSet.checkerSetId) ? this.currentHoverItem === index ? this.$t('取消选中') : this.$t('已选中') : this.$t('选择')
                }
                return txt
            },
            getIconColorClass (checkerSetId) {
                return checkerSetId ? `c${(checkerSetId[0].charCodeAt() % 6) + 1}` : 'c1'
            },
            getCodeLang (codeLang) {
                const names = this.toolMeta.LANG.map(lang => {
                    if (lang.key & codeLang) {
                        return lang.name
                    }
                }).filter(name => name)
                return names.join('、')
            },
            handleOption (isDisabled, isInstall, checkerSet, isCancel) {
                if (!isDisabled) {
                    if (isInstall) this.install(checkerSet)
                    else this.handleSelect(checkerSet, isCancel)
                }
            },
            handleKeyWordSearch (value) {
                this.keyWord = value.trim()
                this.params.quickSearch = this.keyWord
                this.classifyCode = 'store'
                this.resetScroll()
                this.requestList(false, this.params)
            },
            handleClear (str) {
                if (str === '') {
                    this.keyWord = ''
                    this.handleKeyWordSearch('')
                }
            },
            async refresh () {
                await this.getRuleSetList()
                if (this.keyWord === '') {
                    this.requestList(true)
                } else {
                    this.keyWord = this.keyWord.trim()
                    const params = { quickSearch: this.keyWord }
                    this.requestList(true, params)
                }
            },
            resetScroll () {
                const target = document.querySelector('.checkerset-panel')
                if (target) target.scrollTop = 0
                this.params.pageNum = 1
            },
            scrollLoadMore (event) {
                const target = event.target
                const bottomDis = target.scrollHeight - target.clientHeight - target.scrollTop
                if (bottomDis < 10 && !this.loadEnd && !this.isLoadingMore) {
                    this.params.pageNum++
                    this.pageChange = true
                    this.requestList(false, this.params)
                }
            },
            addScrollLoadMore () {
                this.$nextTick(() => {
                    const mainBody = document.querySelector(`.checkerset-panel`)
                    if (mainBody) mainBody.addEventListener('scroll', this.scrollLoadMore, { passive: true })
                })
            },
            removeScrollLoadMore () {
                const mainBody = document.querySelector('.checkerset-panel')
                if (mainBody) mainBody.removeEventListener('scroll', this.scrollLoadMore, { passive: true })
            },
            install (checkerSet) {
                const params = {
                    type: 'PROJECT',
                    projectId: this.projectId,
                    checkerSetId: checkerSet.checkerSetId,
                    version: checkerSet.version
                }
                this.loading = true
                this.$store.dispatch('install', params).then(res => {
                    if (res.code === '0') {
                        this.$bkMessage({ theme: 'success', message: this.$t('安装成功') })
                        this.refresh()
                    }
                }).catch(e => {
                    this.$bkMessage({
                        message: this.$t('安装失败'),
                        theme: 'error'
                    })
                })
            },
            getToolTips (hasMultiLang, notCurLang) {
                if (hasMultiLang) {
                    return this.$t('该规则集不适用于当前插件')
                } else if (notCurLang) {
                    return this.$t('该规则集不适用于当前插件已选择的语言')
                }
                return { disabled: true }
            },
            handleTipsConfirm () {
                window.localStorage.setItem('ruleSetConfirm', 1)
            }
        }
    }
</script>
<style lang="scss">
    .install-more-dialog {
        border: 1px solid #ebf0f5;
        p {
            margin: 0;
            padding: 0;
        }
        .bk-dialog-tool {
            display: none;
        }
        .bk-dialog .bk-dialog-body {
            padding: 8px 16px 0;
        }
        .main-content {
            height: 100%;
            padding-top: 10px;
        }
        .info-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            span {
                color: #222222;
                font-size: 14px;
            }
        }
        .checkerset-fresh {
            cursor: pointer;
            display: inline-block;
            font-size: 14px;
            padding: 4px;
            margin-left: 3px;
            position: relative;
            color: #3c96ff;;
            &.spin-icon {
                color: #c3cdd7;
            }
        }
        .handle-option {
            display: flex;
            .icon-close {
                margin-left: 10px;
                font-size: 28px;
                color: #979ba5;
                cursor: pointer;
                z-index: 3000;
            }
        }
        .search-input {
            width: 120px;
        }
        .search-select {
            margin-right: 10px;
            width: 100px;
        }
        .checkerset-tab {
            height: calc(100% - 40px);
            border: 0;
            font-size: 12px;
            font-weight: 500;
            overflow: hidden;
            div.bk-tab-section {
                height: calc(100% - 42px);
                overflow-y: hiden;
                padding: 0;
                .bk-tab-content {
                    height: 100%;
                    overflow: auto;
                    &::-webkit-scrollbar {
                        width: 6px;
                    }
                }
            }
            .bk-tab-header {
                .bk-tab-label-wrapper {
                    .bk-tab-label-list {
                        .bk-tab-label-item {
                            padding: 0 15px;
                            min-width: auto;
                            .bk-tab-label {
                                font-size: 12px;
                                color: #63656e;
                                &.active {
                                    font-weight: bold;
                                }
                            }
                        }
                    }
                }
            }
        }
        .info-card {
            display: flex;
            align-items: center;
            margin: 0 0 6px;
            padding: 0 10px 0 8px;
            height: 66px;
            &:first-child {
                margin-top: 12px;
            }
            .checkerset-icon {
                width: 48px;
                height: 48px;
                font-size: 24px;
                font-weight: bold;
                margin-right: 14px;
                text-align: center;
                line-height: 48px;
                color: #fff;
                border-radius: 8px;
                &.c1 { background: #37dab9; }
                &.c2 { background: #7f6efa; }
                &.c3 { background: #ffca2b; }
                &.c4 { background: #fe8f65; }
                &.c5 { background: #f787d9; }
                &.c6 { background: #5e7bff; }
            }
            .logo {
                width: 50px;
                height: 50px;
                font-size: 50px;
                line-height: 50px;
                margin-right: 15px;
                color: #c3cdd7;
            }
            .checkerset-main {
                display: flex;
                align-items: center;
                line-height: 14px;
            }
            .info-content {
                padding: 24px 0 20px;
                flex: 1;
                color: #4A4A4A;
                font-size: 14px;
                padding-right: 10px;
                flex-direction: column;
                justify-content: space-between;
                overflow: hidden;
                .name {
                    max-width: 240px;
                    white-space: nowrap;
                    overflow: hidden;
                    color: #222222;
                    font-size: 14px;
                    font-weight: bold;
                    text-overflow: ellipsis;
                    &:hover {
                        color: #3a84ff;
                        cursor: pointer;
                    }
                }
                .use-mark {
                    margin-left: 8px;
                    font-size: 12px;
                    height: 20px;
                    display: inline-block;
                    padding: 2px 10px;
                    border-radius: 2px;
                    white-space: nowrap;
                    &.preferred {
                        background-color: rgba(134, 223, 38, 0.3);
                        color: rgba(53, 99, 22, 0.8);
                        border: 1px solid rgba(102, 197, 1, 0.3);
                    }
                    &.recommend {
                        background-color: rgba(211, 224, 255, 0.3);
                        color: rgba(61, 76, 138, 0.8);
                        border: 1px solid rgba(187, 204, 244, 0.3);
                    }
                }
                .language {
                    max-width: 240px;
                    white-space: nowrap;
                    overflow: hidden;
                    display: inline;
                    margin-left: 8px;
                    padding-left: 8px;
                    font-size: 12px;
                    color: #63656e;
                    text-overflow: ellipsis;
                    border-left: 1px solid #d8d8d8;
                }
            }
            .checkerset-desc {
                text-overflow: ellipsis;
                overflow: hidden;
                width: 100%;
                white-space: nowrap;
                font-size: 12px;
                color: #666666;
                position: relative;
                top: 2px;
                font-weight: normal;
            }
            .other-msg {
                font-size: 12px;
                color: #bbbbbb;
                span:first-child {
                    width: 150px;
                    display: inline-block;
                }
                span:last-child {
                    margin-left: 10px;
                }
            }
            .handle-btn {
                width: 74px;
                line-height: 22px;
                font-weight: normal;
                &.enable-btn:hover {
                    background-color: #3a84ff;
                    color: white;
                }
                &.disable-btn {
                    background-color: #fff;
                    border-color: #dcdee5;
                    color: #c4c6cc;
                    cursor: not-allowed;
                }
            }
            &.disabled .info-content {
                .name,
                .language,
                .checkerset-desc,
                .other-msg {
                    color: #c3cdd7;
                }
            }
            &.selected {
                background-color: #f3f7fe;
            }
        }
        .info-card:hover {
            background-color: #E9F4FF;
        }
        .search-result {
            .info-card:first-child {
                margin-top: 0;
            }
            &::-webkit-scrollbar {
                width: 6px;
            }
            height: calc(100% - 60px);
            margin-top: 20px;
            overflow: auto;
        }
        .codecc-table-empty-text {
            text-align: center;
            margin-top: 180px;
        }
        .codecc-link {
            position: relative;
            top: 4px;
            padding-right: 12px;
            text-decoration: none;
            color: #3a84ff;
            cursor: pointer;
        }
    }
</style>
