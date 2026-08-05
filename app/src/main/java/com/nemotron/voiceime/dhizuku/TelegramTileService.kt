package com.nemotron.voiceime.dhizuku

import com.nemotron.voiceime.R

/** Tile para congelar/descongelar Telegram. */
class TelegramTileService : AppFreezeTileService() {
    override val targetPackage: String = "org.telegram.messenger"
    override val tileLabel: String = "Telegram"
    override val tileIconRes: Int = R.drawable.ic_telegram_tile
}
