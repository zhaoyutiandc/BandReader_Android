package com.example.bandReader.data

sealed class SyncStatus(
    var str: String = "同步手环"
) {
    data object SyncNoConn : SyncStatus("未连接")
    data object SyncNoApp : SyncStatus("APP未安装")
    data object Syncing : SyncStatus("同步中")
    data object SyncDef : SyncStatus("同步手环")
    data object SyncFail : SyncStatus("同步失败")
    data object SyncRe : SyncStatus("重新同步")
}

sealed class SyncType {
    data object UnSync : SyncType()
    data class Range(val start: Int, val end: Int) : SyncType()
}