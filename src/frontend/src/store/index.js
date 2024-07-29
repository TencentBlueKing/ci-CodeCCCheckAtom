/* eslint-disable no-unused-vars */
import Vue from 'vue';
import Vuex from 'vuex';
import request from '../utils/request';

Vue.use(Vuex);

const store = new Vuex.Store({
  state: {
    projectId: '',
    pipelineId: '',
    toolMeta: '',
    toolList: [],
    extraHeight: 130,
    categoryList: [],
    codeLangs: [],
    checkerSetLanguage: [],
  },
  getters: {
  },
  mutations: {
    updateToolMeta(state, toolMeta) {
      state.toolMeta = Object.assign({}, toolMeta);
    },
    updateToolList(state, toolList) {
      state.toolList = Object.assign([], toolList);
    },
    updateProjectId(state, projectId) {
      state.projectId = projectId;
    },
    updatePipelineId(state, pipelineId) {
      state.pipelineId = pipelineId;
    },
    updateExtraHeight(state, height) {
      state.extraHeight = height;
    },
    updateCategoryList(state, categoryList) {
      state.categoryList = categoryList;
    },
    updateCodeLangs(state, codeLangs) {
      state.codeLangs = codeLangs;
    },
    updateCheckerSetLanguage(state, checkerSetLanguage) {
      state.checkerSetLanguage = checkerSetLanguage;
    },
  },
  actions: {
    getToolMeta({ commit, state }) {
      const params = { metadataType: 'LANG;TOOL_TYPE;TOOL_PATTERN;PARAM_TYPE' };
      return request.get(`${window.CODECC_SITE_URL}/ms/task/api/user/metadatas`, { params }).then((res) => {
        const toolMeta = res.data || {};
        commit('updateToolMeta', toolMeta);
        return toolMeta;
      })
        .catch((e) => {
          console.error(e);
        });
    },
    getToolList({ commit, state }) {
      const params = { isDetail: false };
      return request.get(`${window.CODECC_SITE_URL}/ms/task/api/user/toolList`, { params }).then((res) => {
        const toolList = res.data || [];
        commit('updateToolList', toolList);
        return toolList;
      })
        .catch((e) => {
          console.error(e);
        });
    },
    count({ commit }, params) {
      return request.post(`${window.CODECC_SITE_URL}/ms/defect/api/user/checkerSet/count`, params).then((res) => {
        const checkerSetLanguage = res.data || [];
        commit('updateCheckerSetLanguage', checkerSetLanguage);
        return checkerSetLanguage;
      })
        .catch((e) => {
          console.error(e);
        });
    },
    params({ commit }) {
      return request.get(`${window.CODECC_SITE_URL}/ms/defect/api/user/checkerSet/params`).then((res) => {
        const categoryList = res.data.catatories || [];
        const codeLangs = res.data.codeLangs || [];
        commit('updateCategoryList', categoryList);
        commit('updateCodeLangs', codeLangs);
        return categoryList;
      })
        .catch((e) => {
          console.error(e);
        });
    },
    listPageable({ commit }, params) {
      return request.post(`${window.CODECC_SITE_URL}/ms/defect/api/user/checkerSet/listPageable`, params).then(res => res.data || {})
        .catch((e) => {
          console.error(e);
        });
    },
    otherList({ commit }, params) {
      return request.post(`${window.CODECC_SITE_URL}/ms/defect/api/user/checkerSet/otherList`, params).then(res => res.data || {})
        .catch((e) => {
          console.error(e);
        });
    },
    install({ commit }, params) {
      const { checkerSetId } = params;
      return request.post(`${window.CODECC_SITE_URL}/ms/defect/api/user/checkerSet/${checkerSetId}/relationships`, params).then(res => res || {})
        .catch((e) => {
          console.error(e);
        });
    },
    taskList({ commit }, params) {
      return request.post(`${window.CODECC_SITE_URL}/ms/task/api/user/task/taskSortType/CREATE_DATE`, params).then(res => res.data || {})
        .catch((e) => {
          console.error(e);
        });
    },
    customToolList({ commit }) {
      return request.get(`${window.CODECC_SITE_URL}/ms/task/api/user/getOpenScanAndEpcToolNameMap`).then(res => res.data || {})
        .catch((e) => {
          console.error(e);
        });
    },
    getTaskIssue({ commit, state }, params) {
      return request.get(`${window.CODECC_SITE_URL}/ms/task/api/user/issue/pipeline/${state.pipelineId}/getTaskIssue`).then(res => res.data || {})
        .catch((e) => {
          console.error(e);
        });
    },
    getLastAnalyzeTool({ commit, state }, params) {
      const { multiPipelineMark } = params;
      const queryObj = multiPipelineMark ? `?multiPipelineMark=${multiPipelineMark}` : '';
      return request.get(`${window.CODECC_SITE_URL}/ms/defect/api/user/tool/pipeline/${state.pipelineId}/getLastAnalyzeTool${queryObj}`).then(res => res.data || {})
        .catch((e) => {
          console.error(e);
        });
    },
    getOpenScanAndPreProdCheckerSet({ commit, state }, params) {
      return request.get(`${window.CODECC_SITE_URL}/ms/task/api/user/getOpenScanAndPreProdCheckerSetMap`).then(res => res.data || {})
        .catch((e) => {
          console.error(e);
        });
    },
  },
});

export default store;
