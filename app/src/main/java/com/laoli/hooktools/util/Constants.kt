package com.laoli.hooktools.util

/**
 * 全局常量:包名、SharedPreferences 名、资源目标等。
 *
 * 模块 UI 与 Hook 类共享这些常量。
 */
object Constants {
    /** 目标应用(好友圈)包名 */
    const val TARGET_PACKAGE = "com.xtc.moment"

    /** 运动应用包名 */
    const val SPORT_PACKAGE = "com.xtc.sport"

    /** 个人中心应用包名 */
    const val PERSONAL_CENTER_PACKAGE = "com.xtc.personalcenter"

    /** 模块自身包名 */
    const val MODULE_PACKAGE = "com.laoli.hooktools"

    /** 公告/更新后端 API 地址 */
    const val API_BASE_URL = "https://laolixp.cpolar.top"

    /** 模块 UI 侧配置 SharedPreferences 文件名 */
    const val PREFS_CONFIG = "laoli_config"

    /** 宿主侧激活回传 SharedPreferences 文件名(Hook 写入,模块 UI 读取) */
    const val PREFS_ACTIVE = "laoli_xp_active"

    // ---- 配置 key ----
    const val KEY_HEAD_BG_ENABLED = "head_bg_enabled"
    const val KEY_HEAD_BG_PATH = "head_bg_path"
    const val KEY_MSG_BG_ENABLED = "msg_bg_enabled"
    const val KEY_MSG_BG_PATH = "msg_bg_path"

    // ---- string 替换配置 key ----
    const val KEY_STRING_APP_NAME_ENABLED = "string_app_name_enabled"
    const val KEY_STRING_APP_NAME_VALUE = "string_app_name_value"

    // ---- color 替换配置 key ----
    const val KEY_COLOR_BANNER_ENABLED = "color_banner_enabled"
    const val KEY_COLOR_BANNER_VALUE = "color_banner_value"
    const val KEY_COLOR_PUBLISH_ENABLED = "color_publish_enabled"
    const val KEY_COLOR_PUBLISH_VALUE = "color_publish_value"
    const val KEY_COLOR_NAME_ENABLED = "color_name_enabled"
    const val KEY_COLOR_NAME_VALUE = "color_name_value"

    // ---- 时间详细显示配置 key ----
    const val KEY_TIME_DETAIL_ENABLED = "time_detail_enabled"

    // ---- 防删除(只防同步删除)配置 key ----
    const val KEY_ANTI_DELETE_ENABLED = "anti_delete_enabled"

    // ---- 链接自动跳转配置 key ----
    const val KEY_LINK_JUMP_ENABLED = "link_jump_enabled"

    // ---- 自定义字体配置 key ----
    const val KEY_FONT_ENABLED = "font_enabled"
    const val KEY_FONT_PATH = "font_path"

    // ---- 模块自身字体配置 key ----
    const val KEY_MODULE_FONT_ENABLED = "module_font_enabled"
    const val KEY_MODULE_FONT_PATH = "module_font_path"

    // ---- 运动配置 key ----
    const val KEY_SPORT_ENERGY_ENABLED = "sport_energy_enabled"
    const val KEY_SPORT_ENERGY_VALUE = "sport_energy_value"
    const val KEY_SPORT_RED_RING_ENABLED = "sport_red_ring_enabled"
    const val KEY_SPORT_RED_RING_COUNT = "sport_red_ring_count"
    const val KEY_SPORT_FONT_ENABLED = "sport_font_enabled"
    const val KEY_SPORT_FONT_PATH = "sport_font_path"
    const val KEY_SPORT_AVATAR_ENABLED = "sport_avatar_enabled"
    const val KEY_SPORT_AVATAR_PATH = "sport_avatar_path"
    const val KEY_SPORT_RANK_ENABLED = "sport_rank_enabled"
    const val KEY_SPORT_RANK_VALUE = "sport_rank_value"
    const val KEY_SPORT_RANK_ENERGY_ENABLED = "sport_rank_energy_enabled"
    const val KEY_SPORT_RANK_ENERGY_VALUE = "sport_rank_energy_value"

    // ---- 个人中心配置 key ----
    const val KEY_PC_NAME_ENABLED = "pc_name_enabled"
    const val KEY_PC_NAME_VALUE = "pc_name_value"
    const val KEY_PC_SCORE_ENABLED = "pc_score_enabled"
    const val KEY_PC_SCORE_VALUE = "pc_score_value"
    const val KEY_PC_REALNAME_ENABLED = "pc_realname_enabled"
    const val KEY_PC_REALNAME_VALUE = "pc_realname_value"
    const val KEY_PC_FONT_ENABLED = "pc_font_enabled"
    const val KEY_PC_FONT_PATH = "pc_font_path"
    const val KEY_PC_NAME_COLOR_ENABLED = "pc_name_color_enabled"
    const val KEY_PC_NAME_COLOR_VALUE = "pc_name_color_value"
    const val KEY_PC_BG_BOY_ENABLED = "pc_bg_boy_enabled"
    const val KEY_PC_BG_BOY_PATH = "pc_bg_boy_path"
    const val KEY_PC_BG_GIRL_ENABLED = "pc_bg_girl_enabled"
    const val KEY_PC_BG_GIRL_PATH = "pc_bg_girl_path"

    /** 自定义字体文件在外部存储中的文件名 */
    const val FONT_FILE_NAME = "custom_font.ttf"
    /** 运动自定义字体文件名 */
    const val SPORT_FONT_FILE_NAME = "sport_font.ttf"
    /** 个人中心自定义字体文件名 */
    const val PC_FONT_FILE_NAME = "pc_font.ttf"
    /** 自定义字体文件最大字节数(20MB) */
    const val FONT_MAX_BYTES = 20L * 1024 * 1024

    /** 下载字体存放子目录名(位于 /sdcard/laoli_hooktools/ 下) */
    const val FONT_DOWNLOAD_DIR_NAME = "fonts"

    /** 详细时间显示格式(完整年月日 时分) */
    const val TIME_DETAIL_FORMAT = "yyyy-MM-dd HH:mm"

    // ---- 激活回传 key(Hook 侧写,模块侧读) ----
    const val KEY_ACTIVE_FLAG = "active"
    const val KEY_ACTIVE_TIME = "last_active"
    const val KEY_ACTIVE_VERSION = "module_version"

    /**
     * 可替换的目标 drawable 资源定义。
     */
    enum class TargetResource(
        val resName: String,
        val configKeyEnabled: String,
        val configKeyPath: String,
        /** View 的 id 资源名(如 iv_head_bg),用于运行时 getIdentifier 反查真实 id */
        val viewIdName: String? = null
    ) {
        BG_HEAD_VIEW(
            resName = "bg_head_view",
            configKeyEnabled = KEY_HEAD_BG_ENABLED,
            configKeyPath = KEY_HEAD_BG_PATH,
            viewIdName = "iv_head_bg"
        ),
        BG_NEW_MESSAGE(
            resName = "bg_new_message",
            configKeyEnabled = KEY_MSG_BG_ENABLED,
            configKeyPath = KEY_MSG_BG_PATH
        );

        companion object {
            /** 原始资源路径(仅作展示用) */
            fun originalPath(res: TargetResource): String =
                "res/drawable-xhdpi-v4/${res.resName}.png"
        }
    }

    /**
     * 可替换的 string 资源定义。
     */
    enum class TargetString(
        val resName: String,
        val configKeyEnabled: String,
        val configKeyValue: String,
        val defaultValue: String,
        /** 显示该字符串的 View 的 id 资源名(用于遍历兜底替换) */
        val viewIdName: String? = null
    ) {
        APP_NAME(
            resName = "app_name",
            configKeyEnabled = KEY_STRING_APP_NAME_ENABLED,
            configKeyValue = KEY_STRING_APP_NAME_VALUE,
            defaultValue = "好友圈",
            viewIdName = "tv_moment_banner"
        )
    }

    /**
     * 可替换的 color 资源定义。
     */
    enum class TargetColor(
        val resName: String,
        val configKeyEnabled: String,
        val configKeyValue: String,
        /** 默认颜色值(十六进制 #RRGGBB 或 #AARRGGBB) */
        val defaultValue: String,
        /** 显示该颜色的 View 的 id 资源名(遍历兜底直接 setTextColor) */
        val viewIdName: String? = null
    ) {
        BANNER_TEXT(
            resName = "banner_text_color",
            configKeyEnabled = KEY_COLOR_BANNER_ENABLED,
            configKeyValue = KEY_COLOR_BANNER_VALUE,
            defaultValue = "#f4a21d",
            viewIdName = "tv_moment_banner"
        ),
        PUBLISH_TEXT(
            resName = "publish_text_color",
            configKeyEnabled = KEY_COLOR_PUBLISH_ENABLED,
            configKeyValue = KEY_COLOR_PUBLISH_VALUE,
            defaultValue = "#ffffff",
            viewIdName = "tvPublishAdd"
        ),
        NAME_TEXT(
            resName = "name_text_color",
            configKeyEnabled = KEY_COLOR_NAME_ENABLED,
            configKeyValue = KEY_COLOR_NAME_VALUE,
            defaultValue = "#fafafa",
            viewIdName = "moment_name_tv"
        )
    }
}
