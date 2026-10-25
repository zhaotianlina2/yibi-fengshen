package com.ztl.yibi

import android.graphics.Paint
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/* ============================ 音效：AudioTrack 静态缓冲，启动时波形合成 ============================ */

internal class Sfx {
    private val tracks = ArrayList<AudioTrack>(16)
    private lateinit var write: AudioTrack
    private lateinit var heng: AudioTrack
    private lateinit var shu: AudioTrack
    private lateinit var pie: AudioTrack
    private lateinit var dian: AudioTrack
    private lateinit var kou: AudioTrack
    private lateinit var zhe: AudioTrack
    private lateinit var die: AudioTrack
    private lateinit var hurt: AudioTrack
    private lateinit var wave: AudioTrack
    private lateinit var over: AudioTrack
    private lateinit var clearW: AudioTrack

    fun init() {
        val sr = 44100
        fun synth(dur: Double, f: (Double) -> Double): ShortArray {
            val n = (dur * sr).toInt()
            val out = ShortArray(n)
            for (i in 0 until n) out[i] = (f(i / sr.toDouble()).coerceIn(-1.0, 1.0) * 31000.0).toInt().toShort()
            return out
        }
        var seed = 1L
        fun noise(): Double { seed = seed * 6364136223846793005L + 1442695040888963407L; return (seed shr 33) / 4503599627370496.0 - 1.0 }

        write = mk(synth(0.22) { t -> (noise() * 0.5 + noise() * 0.3) * exp(-t * 6.0) * min(1.0, t / 0.01) }, 0.4f)
        heng = mk(synth(0.3) { t -> sin(2 * PI * (1400 - 2600 * t) * t) * exp(-t * 9.0) * min(1.0, t / 0.004) + noise() * 0.25 * exp(-t * 14.0) }, 0.75f)
        shu = mk(synth(0.55) { t ->
            val boom = sin(2 * PI * (150 * exp(-t * 3.0) + 48) * t) * exp(-t * 4.5)
            val crack = noise() * exp(-t * 11.0) * min(1.0, t / 0.002) * (if (t < 0.12) 1.0 else 0.25)
            boom + crack * 0.9
        }, 0.85f)
        pie = mk(synth(0.4) { t -> noise() * sin(2 * PI * (5 + 6 * t) * t) * exp(-t * 5.0) * min(1.0, t / 0.05) }, 0.55f)
        dian = mk(synth(0.12) { t -> sin(2 * PI * 880 * (1 + 2 * t) * t) * exp(-t * 26.0) * min(1.0, t / 0.003) }, 0.6f)
        kou = mk(synth(0.45) { t -> (sin(2 * PI * 110 * t) + 0.4 * sin(4 * PI * 110 * t)) * min(1.0, t / 0.05) * exp(-t * 3.0) }, 0.7f)
        zhe = mk(synth(0.2) { t -> sin(2 * PI * 660 * t) * exp(-t * 12.0) * min(1.0, t / 0.002) + 0.3 * sin(2 * PI * 1320 * t) * exp(-t * 16.0) }, 0.6f)
        die = mk(synth(0.14) { t -> sin(2 * PI * (260 - 900 * t) * t) * exp(-t * 18.0) * min(1.0, t / 0.003) }, 0.55f)
        hurt = mk(synth(0.3) { t -> sin(2 * PI * (90 - 40 * t) * t) * exp(-t * 8.0) * min(1.0, t / 0.004) + noise() * 0.2 * exp(-t * 20.0) }, 0.8f)
        wave = mk(synth(0.22) { t -> sin(2 * PI * (170 - 90 * t) * t) * exp(-t * 10.0) * min(1.0, t / 0.003) }, 0.8f)
        over = mk(synth(0.8) { t ->
            val f = if (t < 0.25) 392.0 else if (t < 0.5) 311.0 else 233.0
            sin(2 * PI * f * t) * exp(-(t % 0.25) * 5.0) * min(1.0, t / 0.01)
        }, 0.75f)
        clearW = mk(synth(0.35) { t ->
            val f = if (t < 0.15) 659.0 else 880.0
            (sin(2 * PI * f * t) + 0.3 * sin(4 * PI * f * t)) * exp(-(t % 0.17) * 8.0) * min(1.0, t / 0.005)
        }, 0.65f)
    }

    private fun mk(pcm: ShortArray, vol: Float): AudioTrack {
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            )
            .setAudioFormat(
                AudioFormat.Builder().setSampleRate(44100)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        t.write(pcm, 0, pcm.size)
        t.setVolume(vol)
        tracks += t
        return t
    }

    private fun play(t: AudioTrack?) = try { t?.play() } catch (_: Exception) {}
    fun write() = play(write)
    fun heng() = play(heng)
    fun shu() = play(shu)
    fun pie() = play(pie)
    fun dian() = play(dian)
    fun kou() = play(kou)
    fun zhe() = play(zhe)
    fun die() = play(die)
    fun hurt() = play(hurt)
    fun wave() = play(wave)
    fun over() = play(over)
    fun clearW() = play(clearW)
    fun release() { tracks.forEach { runCatching { it.release() } }; tracks.clear() }
}

/* ============================ 笔画识别：轨迹度量法 ============================ */

internal enum class Spell { HENG, SHU, PIE, DIAN, KOU, ZHE }

internal class Pt(val x: Float, val y: Float)

internal object StrokeMagic {
    /**
     * 度量法识别：
     *  丶 总长极短
     *  一 近似直线、横向为主
     *  丨 近似直线、纵向为主
     *  丿 近似直线、左下方向
     *  口 轨迹长但位移很小（闭合/回环）
     *  乙 位移右下、路径明显长于位移（多段折）
     */
    fun recognize(pts: List<Pt>, screenW: Float, screenH: Float): Spell? {
        if (pts.size < 3) return null
        var len = 0f
        var minX = pts[0].x; var maxX = minX; var minY = pts[0].y; var maxY = minY
        for (i in 1 until pts.size) {
            len += hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y)
            if (pts[i].x < minX) minX = pts[i].x
            if (pts[i].x > maxX) maxX = pts[i].x
            if (pts[i].y < minY) minY = pts[i].y
            if (pts[i].y > maxY) maxY = pts[i].y
        }
        val dx = pts.last().x - pts.first().x
        val dy = pts.last().y - pts.first().y
        val net = hypot(dx, dy)
        if (len < screenW * 0.055f) return Spell.DIAN
        val straight = len < net * 1.38f + 8f
        val bw = maxX - minX; val bh = maxY - minY

        val spell = when {
            straight && abs(dx) > 2.4f * abs(dy) -> Spell.HENG
            straight && abs(dy) > 2.4f * abs(dx) -> Spell.SHU
            straight && dx < 0f && dy > 0f && abs(dy) > 0.4f * abs(dx) && abs(dx) > 0.4f * abs(dy) -> Spell.PIE
            net < len * 0.32f && len > screenW * 0.17f && bw > screenW * 0.10f && bh > screenW * 0.08f -> Spell.KOU
            dx > screenW * 0.10f && dy > screenH * 0.05f && len > screenW * 0.16f && bw > screenW * 0.10f && bh > screenW * 0.06f -> Spell.ZHE
            else -> null
        }
        Log.i("YIBI", "recognize len=${len.toInt()} net=${net.toInt()} bw=${bw.toInt()} bh=${bh.toInt()} -> $spell")
        return spell
    }
}

/* ============================ 实体 ============================ */

internal class Enemy(var x: Float, var y: Float, val r: Float, val kind: String, var hp: Int, val speed: Float, var slow: Float = 0f) {
    var t = Random.nextFloat() * 6.28f
    var dead = false
    var slashMark = 0
}

internal class Slash(var y: Float, val speed: Float, val id: Int)
internal class Bolt(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float) { var dead = false }
internal class ZBlade(var x: Float, var y: Float, var vx: Float, var vy: Float, var hits: Int) { var dead = false }
internal class Lightning(var x: Float, var y: Float, var life: Float) { var dead = false }
internal class Shield(var hp: Int, var time: Float)
internal class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val max: Float, val size: Float, val color: Color)
internal class FloatText(var x: Float, var y: Float, val text: String, var life: Float, val color: Color, val size: Float)
internal class Trail(val pts: List<Pt>, var life: Float, val cast: Spell?)

internal val INK_BLACK = Color(0xFF23212B)
internal val INK_RED = Color(0xFFC3272B)
internal val PAPER_TOP = Color(0xFFF8F2E4)
internal val PAPER_BOT = Color(0xFFEDE1C6)
internal val PAPER_LINE = Color(0xFFB7A98A)

/* ============================ 游戏引擎 ============================ */

internal class GameEngine {
    var w = 0f; var h = 0f
    var state = "title"                      // title | playing | over
    var hp = 3
    var ink = 100f
    var wave = 1
    var score = 0
    var best = 0
    var combo = 0
    var comboT = 0f
    var toSpawn = 0
    var spawnT = 0f
    var banner = ""
    var bannerSub = ""
    var bannerT = 0f
    var betweenT = -1f                       // 波间休整（<0 表示未开始）
    var slashId = 0
    var shake = 0f
    var endPause = 0f

    val enemies = ArrayList<Enemy>(40)
    val slashes = ArrayList<Slash>(4)
    val bolts = ArrayList<Bolt>(12)
    val zblades = ArrayList<ZBlade>(4)
    val lightning = ArrayList<Lightning>(4)
    val parts = ArrayList<Particle>(300)
    val floats = ArrayList<FloatText>(16)
    val trails = ArrayList<Trail>(8)
    val stars = ArrayList<Pt>(60)
    var shield: Shield? = null
    var stroke: ArrayList<Pt>? = null

    var haptic: (Int) -> Unit = {}
    var sfx: Sfx? = null
    var save: () -> Unit = {}
    private val rng = Random(20261026)

    fun resize(wdp: Float, hdp: Float) {
        if (wdp == w && hdp == h) return
        w = wdp; h = hdp
        if (stars.isEmpty()) repeat(60) { stars += Pt(rng.nextFloat() * w, rng.nextFloat() * h) }
    }

    fun loadPrefs(best: Int) { this.best = best }

    fun startRun() {
        state = "playing"
        hp = 3; ink = 100f; score = 0; combo = 0
        enemies.clear(); slashes.clear(); bolts.clear(); zblades.clear()
        lightning.clear(); parts.clear(); floats.clear(); trails.clear()
        shield = null
        startWave(1)
    }

    private fun startWave(n: Int) {
        wave = n
        betweenT = -1f
        val boss = n % 5 == 0
        toSpawn = if (boss) 4 else 4 + n * 2
        spawnT = 0.6f
        banner = if (boss) "第 $n 波 · 狂草兽出没" else "第 $n 波"
        bannerSub = when (n) {
            1 -> "写「一」放斩击波！"
            2 -> "写「丨」召落雷！"
            3 -> "写「口」开护盾！"
            else -> if (boss) "大字虽慢，一发入魂！" else ""
        }
        bannerT = 2.4f
        sfx?.wave()
    }

    private fun spawnEnemy() {
        val boss = wave % 5 == 0
        val kind = when {
            boss -> "boss"
            wave >= 3 && rng.nextFloat() < 0.22f -> "armor"
            wave >= 2 && rng.nextFloat() < 0.28f -> "fast"
            else -> "drop"
        }
        val speed = (46f + wave * 7f).coerceAtMost(135f)
        val e = when (kind) {
            "boss" -> Enemy(w / 2, -70f, 52f, kind, 10 + wave, speed * 0.42f)
            "armor" -> Enemy(rng.nextFloat() * (w - 60) + 30, -45f, 26f, kind, 2, speed * 0.6f)
            "fast" -> Enemy(rng.nextFloat() * (w - 40) + 20, -34f, 13f, kind, 1, speed * 1.9f)
            else -> Enemy(rng.nextFloat() * (w - 50) + 25, -40f, 20f, kind, 1, speed)
        }
        enemies += e
    }

    /* ---------- 手写输入 ---------- */
    fun strokeStart(x: Float, y: Float) {
        stroke = ArrayList<Pt>().apply { add(Pt(x, y)) }
        sfx?.write()
    }

    fun strokeMove(x: Float, y: Float) {
        val s = stroke ?: return
        val last = s.last()
        if (hypot(x - last.x, y - last.y) > 3f) s.add(Pt(x, y))
    }

    fun strokeEnd() {
        val s = stroke ?: return
        stroke = null
        if (s.size < 3) {
            // 短触：标题/结算界面视为点击
            if (state != "playing") tapScreen()
            return
        }
        var cx = 0f; var cy = 0f
        s.forEach { cx += it.x; cy += it.y }
        cx /= s.size; cy /= s.size
        val sp = StrokeMagic.recognize(s, w, h)
        trails += Trail(s.toList(), 1.1f, sp)
        if (sp != null && state == "playing") cast(sp, cx, cy)
    }

    private fun cost(sp: Spell) = when (sp) {
        Spell.HENG -> 25f; Spell.SHU -> 22f; Spell.PIE -> 15f
        Spell.DIAN -> 8f; Spell.KOU -> 30f; Spell.ZHE -> 18f
    }

    private fun label(sp: Spell) = when (sp) {
        Spell.HENG -> "一"; Spell.SHU -> "丨"; Spell.PIE -> "丿"
        Spell.DIAN -> "丶"; Spell.KOU -> "口"; Spell.ZHE -> "乙"
    }

    private fun cast(sp: Spell, cx: Float, cy: Float) {
        if (ink < cost(sp)) {
            addFloat(cx, cy, "墨不足", Color(0xFF8A7B5C), 20f)
            return
        }
        ink -= cost(sp)
        combo += 1; comboT = 4f
        val mult = min(combo, 8)
        when (sp) {
            Spell.HENG -> {
                slashes += Slash(h - 180f, 640f, ++slashId)
                sfx?.heng()
            }
            Spell.SHU -> {
                var target: Enemy? = null; var bd = 1e9f
                for (e in enemies) { val d = abs(e.x - cx) + e.y * 0.4f; if (d < bd) { bd = d; target = e } }
                val tx = target?.x ?: cx
                val ty = target?.y ?: cy
                lightning += Lightning(tx, ty, 0.2f)
                var hits = 0
                for (i in enemies.indices.reversed()) {
                    val e = enemies[i]
                    if (abs(e.x - tx) < 72f && e.y > 0f) { damage(e, 3); hits++ }
                }
                if (hits > 0) score += 10 * hits * mult
                shake = 7f
                sfx?.shu()
                addFloat(tx, ty - 46f, "雷！", INK_RED, 32f)
            }
            Spell.PIE -> {
                for (e in enemies) { e.y -= 165f; e.slow = 2.2f }
                repeat(26) {
                    val x = rng.nextFloat() * w
                    parts += Particle(x, h - 210f, 0f, -rng.nextFloat() * 330f - 120f, 0.55f, 0.55f, 3f, Color(0xFF7A8A5A))
                }
                sfx?.pie()
                addFloat(w / 2, h * 0.5f, "旋风！", Color(0xFF5B6B3F), 26f)
            }
            Spell.DIAN -> {
                repeat(3) {
                    val a = -PI / 2 + (it - 1) * 0.5
                    bolts += Bolt(cx, cy, cos(a).toFloat() * 150f, sin(a).toFloat() * 150f, 2.6f)
                }
                sfx?.dian()
            }
            Spell.KOU -> {
                shield = Shield(3, 9f)
                sfx?.kou()
                addFloat(w / 2, h - 280f, "护盾！", INK_RED, 28f)
            }
            Spell.ZHE -> {
                zblades += ZBlade(cx, h - 230f, 340f, -560f, 3)
                sfx?.zhe()
            }
        }
        haptic(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun damage(e: Enemy, dmg: Int) {
        if (e.dead) return
        e.hp -= dmg
        if (e.hp <= 0) {
            e.dead = true
            val mult = min(combo, 8)
            val gain = 10 * mult
            score += gain
            sfx?.die()
            repeat((8 + e.r / 4).toInt()) {
                val a = rng.nextFloat() * 6.28f
                val sp = rng.nextFloat() * 150f + 60f
                parts += Particle(e.x, e.y, cos(a) * sp, sin(a) * sp, 0.5f, 0.5f, e.r / 6f, INK_BLACK)
            }
            if (e.kind == "boss") {
                shake = 12f
                score += gain * 4
                addFloat(e.x, e.y, "狂草兽伏诛 +${gain * 4}", INK_RED, 26f)
            }
        }
    }

    private fun addFloat(x: Float, y: Float, text: String, color: Color, size: Float) {
        floats += FloatText(x.coerceIn(80f, w - 80f), y, text, 1.1f, color, size)
    }

    /* ---------- 帧更新 ---------- */
    fun step(dt: Float) {
        if (w == 0f) return
        if (state != "playing") { updateFx(dt); return }
        ink = (ink + 14f * dt).coerceAtMost(100f)
        comboT -= dt
        if (comboT <= 0f) combo = 0
        bannerT = (bannerT - dt).coerceAtLeast(0f)
        shake = (shake - dt * 26f).coerceAtLeast(0f)
        shield?.let { sh -> sh.time -= dt; if (sh.time <= 0f || sh.hp <= 0) shield = null }

        if (toSpawn > 0) {
            spawnT -= dt
            if (spawnT <= 0f) { spawnEnemy(); toSpawn--; spawnT = 0.55f + rng.nextFloat() * 0.5f }
        } else if (enemies.isEmpty()) {
            if (betweenT < 0f) { betweenT = 0f; score += 25 * wave; sfx?.clearW(); addFloat(w / 2, h * 0.4f, "本波平定 +${25 * wave}", INK_RED, 24f) }
            betweenT += dt
            if (betweenT > 1.6f) startWave(wave + 1)
        }

        val bottom = h - 150f
        val deadList = ArrayList<Enemy>(4)
        for (e in enemies) {
            e.t += dt * 3f
            e.slow = (e.slow - dt).coerceAtLeast(0f)
            val sp = e.speed * (if (e.slow > 0f) 0.45f else 1f)
            e.y += sp * dt
            if (e.kind == "fast") e.x += sin(e.t * 2.1f) * 120f * dt
            if (e.kind == "boss") e.x = w / 2 + sin(e.t * 0.8f) * (w / 3f)
            if (e.y + e.r >= bottom) {
                val sh = shield
                if (sh != null && sh.hp > 0) {
                    sh.hp--; deadList += e; shake = 5f; sfx?.die()
                } else {
                    hp--; deadList += e; shake = 10f; sfx?.hurt()
                    haptic(HapticFeedbackConstants.LONG_PRESS)
                    if (e.kind == "boss") { hp--; addFloat(e.x, e.y - 50f, "重创！", INK_RED, 30f) }
                    if (hp <= 0) { gameOver(); break }
                }
            }
        }
        enemies.removeAll(deadList)

        // 斩击波
        for (i in slashes.indices.reversed()) {
            val s = slashes[i]
            s.y -= s.speed * dt
            for (e in enemies) {
                if (!e.dead && e.slashMark != s.id && abs(e.y - s.y) < e.r + 24f) {
                    e.slashMark = s.id
                    damage(e, 1)
                }
            }
            if (s.y < -30f) slashes.removeAt(i)
        }

        // 灵弹
        for (i in bolts.indices.reversed()) {
            val b = bolts[i]
            b.life -= dt
            if (b.life <= 0f) { bolts.removeAt(i); continue }
            var target: Enemy? = null; var bd = 1e9f
            for (e in enemies) { val d = hypot(e.x - b.x, e.y - b.y); if (d < bd) { bd = d; target = e } }
            target?.let {
                val dx = it.x - b.x; val dy = it.y - b.y; val d = hypot(dx, dy).coerceAtLeast(1f)
                b.vx += dx / d * 900f * dt; b.vy += dy / d * 900f * dt
                val v = hypot(b.vx, b.vy).coerceAtLeast(1f)
                if (v > 430f) { b.vx = b.vx / v * 430f; b.vy = b.vy / v * 430f }
            }
            b.x += b.vx * dt; b.y += b.vy * dt
            for (e in enemies) {
                if (!e.dead && hypot(e.x - b.x, e.y - b.y) < e.r + 8f) {
                    damage(e, 1)
                    bolts.removeAt(i)
                    break
                }
            }
        }

        // 回旋刃
        for (i in zblades.indices.reversed()) {
            val zb = zblades[i]
            zb.x += zb.vx * dt; zb.y += zb.vy * dt
            if (zb.x < 14f || zb.x > w - 14f) { zb.vx = -zb.vx; zb.hits-- }
            for (e in enemies) {
                if (!e.dead && hypot(e.x - zb.x, e.y - zb.y) < e.r + 12f) {
                    damage(e, 2); zb.hits--; break
                }
            }
            if (zb.hits <= 0 || zb.y < -40f) zblades.removeAt(i)
        }

        enemies.removeAll { it.dead }
        updateFx(dt)
    }

    private fun gameOver() {
        state = "over"
        endPause = 0.9f
        sfx?.over()
        if (score > best) { best = score; save() }
    }

    fun tapScreen() {
        if (endPause <= 0f && (state == "title" || state == "over")) startRun()
    }

    private fun updateFx(dt: Float) {
        endPause = (endPause - dt).coerceAtLeast(0f)
        for (i in parts.indices.reversed()) {
            val p = parts[i]
            p.life -= dt
            if (p.life <= 0) { parts.removeAt(i); continue }
            p.x += p.vx * dt; p.y += p.vy * dt; p.vy += 160f * dt
        }
        for (i in floats.indices.reversed()) {
            val f = floats[i]
            f.life -= dt; f.y -= 40f * dt
            if (f.life <= 0) floats.removeAt(i)
        }
        for (i in trails.indices.reversed()) {
            trails[i].life -= dt * 0.8f
            if (trails[i].life <= 0) trails.removeAt(i)
        }
        for (i in lightning.indices.reversed()) {
            lightning[i].life -= dt
            if (lightning[i].life <= 0) lightning.removeAt(i)
        }
    }

    /* ============================ 渲染 ============================ */
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private fun text(c: DrawScope, x: Float, y: Float, s: String, size: Float, color: Color, alpha: Float = 1f) {
        tp.textSize = size
        tp.typeface = Typeface.create("serif", Typeface.BOLD)
        tp.color = android.graphics.Color.argb(
            (color.alpha * alpha * 255).toInt(),
            (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()
        )
        c.drawContext.canvas.nativeCanvas.drawText(s, x, y, tp)
    }

    private fun off(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(x, y)

    private fun drawStrokePath(c: DrawScope, pts: List<Pt>, width: Float, color: Color) {
        if (pts.size < 2) return
        var prev = pts[0]
        for (i in 1 until pts.size) {
            val p = pts[i]
            c.drawLine(color, off(prev.x, prev.y), off(p.x, p.y), strokeWidth = width)
            prev = p
        }
    }

    fun draw(c: DrawScope) {
        // 宣纸底
        c.drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(PAPER_TOP, PAPER_BOT)),
            topLeft = off(0f, 0f), size = androidx.compose.ui.geometry.Size(w, h)
        )
        for (s in stars) c.drawCircle(Color(0xFF6B5D45).copy(alpha = 0.08f), 1.6f, off(s.x, s.y))

        val bottom = h - 150f

        // 底部墨线 + 封印 + 护盾
        c.drawLine(PAPER_LINE, off(24f, bottom), off(w - 24f, bottom), strokeWidth = 3f)
        c.drawLine(INK_RED.copy(alpha = 0.5f), off(24f, bottom + 8f), off(w - 24f, bottom + 8f), strokeWidth = 1.5f)
        shield?.let { sh ->
            val a = (sh.time / 9f).coerceIn(0.25f, 1f)
            c.drawCircle(INK_RED.copy(alpha = 0.10f * a), 220f, off(w / 2, bottom + 40f))
            c.drawCircle(INK_RED.copy(alpha = 0.6f * a), 220f, off(w / 2, bottom + 40f), style = Stroke(width = 4f))
            for (i in 0 until sh.hp) c.drawCircle(INK_RED, 5f, off(w / 2 - 16f + i * 16f, bottom - 14f))
        }
        c.drawRoundRect(
            INK_RED, topLeft = off(w / 2 - 30f, bottom + 24f),
            size = androidx.compose.ui.geometry.Size(60f, 60f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
        )
        text(c, w / 2, bottom + 68f, "封", 40f, Color.White)

        // 旧墨迹
        for (t in trails) {
            val a = t.life.coerceIn(0f, 1f)
            drawStrokePath(c, t.pts, 7f, INK_BLACK.copy(alpha = 0.5f * a))
            t.cast?.let { sp ->
                val mid = t.pts[t.pts.size / 2]
                text(c, mid.x, mid.y + 10f, label(sp), 34f, INK_RED.copy(alpha = 0.9f), alpha = a)
            }
        }
        // 正在书写
        stroke?.let {
            drawStrokePath(c, it, 9f, INK_BLACK)
            if (it.isNotEmpty()) c.drawCircle(INK_RED.copy(alpha = 0.5f), 12f, off(it.last().x, it.last().y))
        }

        // 敌人
        for (e in enemies) {
            val wob = 1f + 0.07f * sin(e.t * 2.3f)
            val r = e.r * wob
            c.drawCircle(INK_BLACK.copy(alpha = 0.13f), r * 1.5f, off(e.x, e.y))
            when (e.kind) {
                "fast" -> {
                    c.drawCircle(INK_BLACK, r, off(e.x, e.y))
                    c.drawLine(INK_BLACK, off(e.x, e.y - r), off(e.x - r * 0.4f, e.y - r * 2.2f), strokeWidth = 4f)
                }
                "armor" -> {
                    c.drawCircle(INK_BLACK.copy(alpha = 0.88f), r, off(e.x, e.y))
                    c.drawCircle(INK_RED.copy(alpha = 0.85f), r * 0.62f, off(e.x, e.y), style = Stroke(width = 3f))
                }
                "boss" -> {
                    c.drawCircle(INK_BLACK.copy(alpha = 0.92f), r, off(e.x, e.y))
                    text(c, e.x, e.y + r * 0.16f, "狂", r * 0.72f, INK_RED)
                }
                else -> c.drawCircle(INK_BLACK, r, off(e.x, e.y))
            }
            c.drawCircle(Color.White.copy(alpha = 0.7f), r * 0.17f, off(e.x - r * 0.3f, e.y - r * 0.32f))
            if (e.kind == "armor" || e.kind == "boss") {
                val ratio = (e.hp.toFloat() / (if (e.kind == "boss") (10 + wave).toFloat() else 2f)).coerceIn(0f, 1f)
                c.drawRect(INK_BLACK.copy(alpha = 0.25f), topLeft = off(e.x - r, e.y - r - 13f), size = androidx.compose.ui.geometry.Size(r * 2, 5f))
                c.drawRect(INK_RED, topLeft = off(e.x - r, e.y - r - 13f), size = androidx.compose.ui.geometry.Size(r * 2 * ratio, 5f))
            }
            if (e.slow > 0f) c.drawCircle(Color(0xFF7A8A5A).copy(alpha = 0.45f), r * 1.25f, off(e.x, e.y), style = Stroke(width = 2f))
        }

        // 斩击波
        for (s in slashes) {
            c.drawLine(INK_BLACK.copy(alpha = 0.85f), off(0f, s.y), off(w, s.y), strokeWidth = 16f)
            c.drawLine(Color.White.copy(alpha = 0.8f), off(0f, s.y - 4f), off(w, s.y - 4f), strokeWidth = 3f)
            c.drawLine(INK_RED.copy(alpha = 0.5f), off(0f, s.y + 11f), off(w, s.y + 11f), strokeWidth = 3f)
        }
        // 落雷
        for (l in lightning) {
            val a = (l.life / 0.2f).coerceIn(0f, 1f)
            var lx = l.x; var ly = -10f
            var seg = 0
            while (ly < l.y) {
                val ny = min(l.y, ly + 60f)
                val nx = l.x + sin(seg * 2.7f) * 14f
                c.drawLine(INK_RED.copy(alpha = 0.8f * a), off(lx, ly), off(nx, ny), strokeWidth = 6f)
                c.drawLine(Color(0xFFFFD54F).copy(alpha = a), off(lx, ly), off(nx, ny), strokeWidth = 2f)
                lx = nx; ly = ny; seg++
            }
        }
        // 灵弹
        for (b in bolts) {
            c.drawCircle(INK_BLACK, 7f, off(b.x, b.y))
            c.drawCircle(INK_RED.copy(alpha = 0.5f), 11f, off(b.x, b.y), style = Stroke(width = 2f))
        }
        // 回旋刃
        for (zb in zblades) {
            c.drawRect(INK_BLACK, topLeft = off(zb.x - 13f, zb.y - 3f), size = androidx.compose.ui.geometry.Size(26f, 6f))
            c.drawRect(INK_BLACK, topLeft = off(zb.x - 3f, zb.y - 13f), size = androidx.compose.ui.geometry.Size(6f, 26f))
            c.drawCircle(INK_RED.copy(alpha = 0.6f), 14f, off(zb.x, zb.y), style = Stroke(width = 2f))
        }
        // 粒子 & 飘字
        for (p in parts) {
            val k = (p.life / p.max).coerceIn(0f, 1f)
            c.drawCircle(p.color.copy(alpha = k), p.size * k, off(p.x, p.y))
        }
        for (f in floats) {
            text(c, f.x, f.y, f.text, f.size, f.color, alpha = (f.life / 1.1f).coerceIn(0f, 1f))
        }

        // HUD
        if (state != "title") {
            text(c, 76f, 52f, "第 $wave 波", 30f, INK_BLACK)
            text(c, 76f, 100f, "$score", 44f, INK_RED)
            if (combo > 1) text(c, w - 96f, 108f, "连笔 x${min(combo, 8)}", 28f, INK_RED)
            for (i in 0 until 3) {
                val on = i < hp
                c.drawRoundRect(
                    (if (on) INK_RED else Color(0xFFB7A98A)).copy(alpha = if (on) 0.9f else 0.35f),
                    topLeft = off(w - 44f - i * 40f, 34f), size = androidx.compose.ui.geometry.Size(30f, 30f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                )
            }
            val inkW = (w - 160f) * ink / 100f
            c.drawRect(Color(0xFFB7A98A).copy(alpha = 0.4f), topLeft = off(80f, h - 62f), size = androidx.compose.ui.geometry.Size(w - 160f, 10f))
            c.drawRect(INK_BLACK, topLeft = off(80f, h - 62f), size = androidx.compose.ui.geometry.Size(inkW, 10f))
            text(c, 52f, h - 50f, "墨", 24f, INK_BLACK)
        }

        // 横幅
        if (bannerT > 0f && state == "playing") {
            val a = min(1f, bannerT / 0.5f)
            text(c, w / 2, h * 0.3f, banner, 40f, INK_RED, alpha = a)
            if (bannerSub.isNotEmpty()) text(c, w / 2, h * 0.3f + 48f, bannerSub, 22f, INK_BLACK.copy(alpha = 0.85f), alpha = a)
        }

        // 覆盖层
        when (state) {
            "title" -> {
                c.drawRect(PAPER_TOP.copy(alpha = 0.55f), topLeft = off(0f, 0f), size = androidx.compose.ui.geometry.Size(w, h))
                text(c, w / 2, h * 0.23f, "一笔封神", 96f, INK_BLACK)
                text(c, w / 2, h * 0.23f + 64f, "以 字 为 剑", 30f, INK_RED)
                text(c, w / 2, h * 0.44f, "一 斩击波 · 丨 落雷 · 丿 旋风", 24f, INK_BLACK)
                text(c, w / 2, h * 0.44f + 42f, "丶 灵弹 · 口 护盾 · 乙 回刃", 24f, INK_BLACK)
                text(c, w / 2, h * 0.44f + 96f, "在屏幕任意处手写笔画即施法", 22f, Color(0xFF6B5D45))
                text(c, w / 2, h * 0.44f + 132f, "写得越快，出手越快", 22f, Color(0xFF6B5D45))
                if (best > 0) text(c, w / 2, h * 0.62f, "最高分 $best", 26f, INK_RED)
                text(c, w / 2, h * 0.78f, "— 点击屏幕，提笔开战 —", 28f, INK_RED)
            }
            "over" -> {
                c.drawRect(PAPER_BOT.copy(alpha = 0.9f), topLeft = off(0f, 0f), size = androidx.compose.ui.geometry.Size(w, h))
                text(c, w / 2, h * 0.34f, "笔 落 败 局", 56f, INK_BLACK)
                text(c, w / 2, h * 0.34f + 80f, "得分 $score", 44f, INK_RED)
                text(c, w / 2, h * 0.34f + 134f, "最高 $best", 26f, INK_BLACK)
                text(c, w / 2, h * 0.34f + 200f, "点击重新提笔", 26f, INK_RED)
            }
            else -> {}
        }

        // 震屏白闪（受击）
        if (shake > 4f) {
            c.drawRect(Color(0xFFC3272B).copy(alpha = (shake - 4f) / 30f), topLeft = off(0f, 0f), size = androidx.compose.ui.geometry.Size(w, h))
        }
    }
}

/* ============================ Compose 外壳 ============================ */

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GameRoot() }
    }
}

@Composable
fun GameRoot() {
    val view = LocalView.current
    val ctx = LocalContext.current
    val densityPx = LocalDensity.current.density
    val sfx = remember { Sfx().apply { init() } }
    DisposableEffect(Unit) { onDispose { sfx.release() } }

    val engine = remember { GameEngine().apply { this.sfx = sfx } }
    engine.haptic = { view.performHapticFeedback(it) }
    val prefs = remember { ctx.getSharedPreferences("yibi", android.content.Context.MODE_PRIVATE) }
    remember {
        engine.loadPrefs(prefs.getInt("best", 0))
        engine.save = { prefs.edit().putInt("best", engine.best).apply() }
        true
    }

    var tick by remember { mutableLongStateOf(0L) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wDp = maxWidth.value
        val hDp = maxHeight.value
        LaunchedEffect(wDp, hDp) { engine.resize(wDp, hDp) }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { engine.tapScreen() })
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { off -> engine.strokeStart(off.x / densityPx, off.y / densityPx) },
                        onDrag = { ch, _ -> engine.strokeMove(ch.position.x / densityPx, ch.position.y / densityPx) },
                        onDragEnd = { engine.strokeEnd() },
                        onDragCancel = { engine.strokeEnd() }
                    )
                }
        ) {
            tick
            scale(densityPx, densityPx, pivot = androidx.compose.ui.geometry.Offset(0f, 0f)) {
                engine.draw(this)
            }
        }
        LaunchedEffect(Unit) {
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last == 0L) 0f else ((now - last) / 1e9).toFloat().coerceIn(0f, 0.05f)
                    last = now
                    engine.step(dt)
                    tick++
                }
            }
        }
    }
}
