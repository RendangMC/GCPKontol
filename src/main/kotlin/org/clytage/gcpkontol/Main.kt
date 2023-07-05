package org.clytage.gcpkontol

import github.scarsz.discordsrv.DiscordSRV
import github.scarsz.discordsrv.dependencies.jda.api.EmbedBuilder
import me.lucko.spark.api.Spark
import me.lucko.spark.api.statistic.StatisticWindow
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.awt.Color
import java.net.http.WebSocket.Listener


class Main : JavaPlugin(), Listener {
    private var spark: Spark? = null
    private var tpsThreshold: Double? = null
    private var broadcastCommand: String? = null
    private var restartDelay: Int? = null
    private var restartCommand: String? = null
    private var tpsCheckingThread: Int? = null
    private var discordBroadcastMessage: String? = null

    override fun onEnable() {
        saveDefaultConfig()

        tpsThreshold = this.config.getDouble("tpsThreshold")
        broadcastCommand = this.config.getString("broadcastCommand")
        restartDelay = this.config.getInt("restartDelay")
        restartCommand = this.config.getString("restartCommand")
        discordBroadcastMessage = this.config.getString("discordBroadcastMessage")

        this.logger.info("TPS Threshold: ${tpsThreshold.toString()}")
        this.logger.info("Restart Delay: ${restartDelay.toString()} seconds")
        this.logger.info("Broadcast Command: ${this.broadcastCommand.toString()}")
        this.logger.info("Restart Command: ${this.restartCommand.toString()}")
        this.logger.info("Version: ${this.description.version}")
        this.logger.info("Author: ${this.description.authors.joinToString(", ")}")

        val provider = Bukkit.getServicesManager().getRegistration(Spark::class.java)
        if (provider != null) {
            spark = provider.provider
            logger.info("Hooked into SparkAPI!")
            val scheduler = server.scheduler
            tpsCheckingThread = scheduler.scheduleSyncRepeatingTask(this, {
                val tps = spark!!.tps()
                val tpsLast5Sec: Double = tps!!.poll(StatisticWindow.TicksPerSecond.SECONDS_5)
                val tpsLast10Sec: Double = tps.poll(StatisticWindow.TicksPerSecond.SECONDS_10)
                if (tpsLast5Sec <= this.tpsThreshold!! && tpsLast10Sec <= this.tpsThreshold!!) {
                    logger.warning("This server overloaded. Starting restart mitigation in ${restartDelay!!} seconds")
                    if (DiscordSRV.getPlugin() !== null) {
                        DiscordSRV.getPlugin().mainTextChannel.sendMessageEmbeds(
                                EmbedBuilder()
                                    .setDescription(discordBroadcastMessage)
                                    .setColor(Color.RED)
                                    .build()
                            )
                    }
                    scheduler.cancelTask(tpsCheckingThread!!)
                    if (restartDelay!! == 0) {
                        dispatchRestart()
                    } else {
                        scheduler.scheduleSyncDelayedTask(this, {
                            dispatchRestart()
                        }, restartDelay!!.toLong() * 20L)
                    }
                }
            }, 0L, 1L)
        }
    }

    override fun onDisable() {
        tpsCheckingThread?.let { server.scheduler.cancelTask(it) }
        logger.info("${description.name} disabled.")
    }

    private fun dispatchRestart() {
        server.dispatchCommand(this.server.consoleSender, this.broadcastCommand!!)
        server.dispatchCommand(this.server.consoleSender, this.restartCommand!!)
    }
}
