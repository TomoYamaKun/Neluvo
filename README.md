# Neluvo


// アプリアイコン切り替え関数
fun changeAppIcon(context: Context, useMoonIcon: Boolean) {
    val pm = context.packageManager
    val moonAlias = ComponentName(context, "co.neluvo.papa.MainActivityMoon")
    val waveAlias = ComponentName(context, "co.neluvo.papa.MainActivityWave")

    if (useMoonIcon) {
        // 月アイコンを有効化、波アイコンを無効化
        pm.setComponentEnabledSetting(
            moonAlias,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        pm.setComponentEnabledSetting(
            waveAlias,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    } else {
        // 波アイコンを有効化、月アイコンを無効化
        pm.setComponentEnabledSetting(
            waveAlias,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        pm.setComponentEnabledSetting(
            moonAlias,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }
    Toast.makeText(context, "アイコンを変更しました！(反映まで数秒かかる場合があります)", Toast.LENGTH_SHORT).show()
}
