package com.laoli.hooktools.util

import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * Root 权限工具类。
 *
 * 通过 `su` 二进制执行命令,需设备已 root(Magisk/KernelSU 等)。
 * 首次执行会触发 SuperUser 授权对话框。
 */
object RootUtil {

    private const val TAG = "LaoliRoot"

    /**
     * 检测设备是否已 root 且已授权。
     *
     * 执行 `su -c id`,若返回包含 `uid=0` 则已授权 root。
     * 首次调用会触发 SuperUser 授权弹窗。
     *
     * @return true 表示已获取 root 权限
     */
    fun hasRootAccess(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            reader.close()
            val errReader = BufferedReader(InputStreamReader(process.errorStream))
            errReader.readText()
            errReader.close()
            val exitCode = process.waitFor()
            Log.d(TAG, "id output=$output, exit=$exitCode")
            exitCode == 0 && output.contains("uid=0")
        } catch (e: IOException) {
            // su 二进制不存在
            Log.d(TAG, "su not found: ${e.message}")
            false
        } catch (e: Throwable) {
            Log.d(TAG, "hasRootAccess failed: ${e.message}")
            false
        }
    }

    /**
     * 以 root 权限执行命令。
     *
     * @param command 要执行的命令(单个字符串,会传给 `su -c`)
     * @return 命令退出码(0 表示成功)
     */
    fun execRoot(command: String): Int {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            reader.close()
            val errReader = BufferedReader(InputStreamReader(process.errorStream))
            val err = errReader.readText()
            errReader.close()
            val exitCode = process.waitFor()
            Log.d(TAG, "exec: $command\n  exit=$exitCode\n  out=$output\n  err=$err")
            exitCode
        } catch (e: Throwable) {
            Log.e(TAG, "execRoot failed: ${e.message}")
            -1
        }
    }

    /**
     * 以 root 权限执行多条命令(单次 su 会话,避免多次授权弹窗)。
     *
     * @param commands 命令列表
     * @return 最后一条命令的退出码
     */
    fun execRootCommands(vararg commands: String): Int {
        if (commands.isEmpty()) return 0
        return try {
            // 用 sh -c 包裹多条命令
            val script = commands.joinToString("\n")
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", script))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            reader.close()
            val errReader = BufferedReader(InputStreamReader(process.errorStream))
            val err = errReader.readText()
            errReader.close()
            val exitCode = process.waitFor()
            Log.d(TAG, "exec multi: ${commands.joinToString(" && ")}\n  exit=$exitCode\n  out=$output\n  err=$err")
            exitCode
        } catch (e: Throwable) {
            Log.e(TAG, "execRootCommands failed: ${e.message}")
            -1
        }
    }
}
