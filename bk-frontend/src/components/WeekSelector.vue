<template>
    <ul>
        <li
            :class="value && value.includes(week.id) ? 'active' : ''"
            @click="selectedWeek(week.id)"
            class="settings-trigger-week"
            v-for="week in weekList"
            :key="week.label">
            {{week.name}}
        </li>
    </ul>
</template>

<script>
    export default {
        name: 'week-selector',
        props: {
            name: {
                type: String,
                default: ''
            },
            value: {
                type: Array,
                default: () => ([])
            },
            handleChange: {
                type: Function,
                default: () => () => {}
            }
        },
        data () {
            return {
                weekList: [
                    {
                        id: 1,
                        name: '一',
                        label: 'Mon'
                    },
                    {
                        id: 2,
                        name: '二',
                        label: 'Tues'
                    },
                    {
                        id: 3,
                        name: '三',
                        label: 'Wed'
                    },
                    {
                        id: 4,
                        name: '四',
                        label: 'Thur'
                    },
                    {
                        id: 5,
                        name: '五',
                        label: 'Fri'
                    },
                    {
                        id: 6,
                        name: '六',
                        label: 'Sat'
                    },
                    {
                        id: 7,
                        name: '日',
                        label: 'Sun'
                    }
                ]
            }
        },
        methods: {
            selectedWeek (id) {
                if (!this.value.includes(id)) {
                    this.value.push(id)
                } else {
                    const i = this.value.indexOf(id)
                    this.value.splice(i, 1)
                }
                this.handleChange(this.name, this.value)
            }
        }
    }
</script>

<style lang="css">
    .settings-trigger-week {
        margin-right: 5px;
        display: inline-block;
        width: 32px;
        height: 32px;
        border-radius: 2px;
        border: 1px solid #c4c6cc;
        cursor: pointer;
        line-height: 32px;
        text-align: center;
    }
    .active {
        border: 1px solid #3a84ff;
        color: #3a84ff;
    }
</style>