export function urlJoin (...args) {
    return args.filter(arg => arg).join('/').replace(/([^:]\/)\/+/g, '$1')
}

export function getQueryParams(urlStr) {
    let url = ''
    if (typeof urlStr === "undefined") {
        url = decodeURI(location.search)
    } else {
        url = "?" + urlStr.split("?")[1]
    }
    let queryObj = new Object()
    if (url.indexOf("?") != -1) {
        const str = url.substr(1)
        const strs = str.split("&")
        for (let i = 0; i < strs.length; i++) {
            queryObj[strs[i].split("=")[0]] = decodeURI(strs[i].split("=")[1])
        }
    }
    return queryObj
}

