package org.autojs.autojs.apkbuilder.keystore

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity
data class KeyStore(
    @PrimaryKey val absolutePath: String,  // 密钥库绝对路径
    @ColumnInfo(name = "filename") val filename: String = "",  // 文件名
    @ColumnInfo(name = "password") val password: String = "",  // 密码
    @ColumnInfo(name = "alias") val alias: String = "",  // 别名
    @ColumnInfo(name = "alias_password") val aliasPassword: String = "",  // 别名密码
    @ColumnInfo(name = "verified") val verified: Boolean = false,  // 验证状态
) {
    override fun toString(): String = filename
}
