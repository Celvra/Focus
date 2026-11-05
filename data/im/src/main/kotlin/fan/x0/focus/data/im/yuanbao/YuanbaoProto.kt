package fan.x0.focus.data.im.yuanbao

import java.io.ByteArrayOutputStream

internal data class HeadMeta(
    val key: String = "",
    val value: String = "",
) {
    companion object
}

internal data class Head(
    val cmdType: Int = 0,
    val cmd: String = "",
    val seqNo: Int = 0,
    val msgId: String = "",
    val module: String = "",
    val needAck: Boolean = false,
    val status: Int = 0,
    val meta: List<HeadMeta> = emptyList(),
) {
    companion object
}

internal data class ConnMsg(
    val head: Head = Head(),
    val data: ByteArray = ByteArray(0),
) {
    companion object
}

internal data class AuthInfo(
    val uid: String = "",
    val source: String = "bot",
    val token: String = "",
)

internal data class DeviceInfo(
    val appVersion: String = "",
    val appOperationSystem: String = "Android",
    val botVersion: String = "",
    val instanceId: String = "16",
)

internal data class AuthBindReq(
    val bizId: String = "ybBot",
    val authInfo: AuthInfo = AuthInfo(),
    val deviceInfo: DeviceInfo = DeviceInfo(),
)

internal data class AuthBindRsp(
    val code: Int = 0,
    val message: String = "",
    val connectId: String = "",
    val timestamp: Long = 0,
    val clientIp: String = "",
) {
    companion object
}

internal data class PingRsp(
    val heartInterval: Int = 0,
    val timestamp: Long = 0,
) {
    companion object
}

internal data class PushMsg(
    val cmd: String = "",
    val module: String = "",
    val msgId: String = "",
    val data: ByteArray = ByteArray(0),
) {
    companion object
}

internal data class DirectedPush(
    val type: Int = 0,
    val content: String = "",
) {
    companion object
}

internal data class KickoutMsg(
    val status: Int = 0,
    val reason: String = "",
    val otherDeviceName: String = "",
) {
    companion object
}

internal data class MsgContent(
    val text: String = "",
    val uuid: String = "",
    val data: String = "",
    val desc: String = "",
    val url: String = "",
    val fileName: String = "",
) {
    companion object
}

internal data class MsgBodyElement(
    val msgType: String = "",
    val msgContent: MsgContent = MsgContent(),
) {
    companion object
}

internal data class InboundMessagePush(
    val callbackCommand: String = "",
    val fromAccount: String = "",
    val toAccount: String = "",
    val senderNickname: String = "",
    val groupId: String = "",
    val groupCode: String = "",
    val groupName: String = "",
    val msgSeq: Long = 0,
    val msgRandom: Long = 0,
    val msgTime: Long = 0,
    val msgKey: String = "",
    val msgId: String = "",
    val msgBody: List<MsgBodyElement> = emptyList(),
    val cloudCustomData: String = "",
    val eventTime: Long = 0,
    val botOwnerId: String = "",
    val clawMsgType: Int = 0,
    val privateFromGroupCode: String = "",
    val traceId: String = "",
) {
    companion object

    fun hasContent(): Boolean =
        callbackCommand.isNotBlank() || fromAccount.isNotBlank() || msgBody.isNotEmpty()
}

internal data class SendC2CMessageReq(
    val msgId: String = "",
    val toAccount: String = "",
    val fromAccount: String = "",
    val msgRandom: Int = 0,
    val msgBody: List<MsgBodyElement> = emptyList(),
    val groupCode: String = "",
)

internal data class SendGroupMessageReq(
    val msgId: String = "",
    val groupCode: String = "",
    val fromAccount: String = "",
    val random: String = "",
    val msgBody: List<MsgBodyElement> = emptyList(),
)

internal data class SendMessageRsp(
    val code: Int = 0,
    val message: String = "",
) {
    companion object
}

internal fun Head.encode(): ByteArray = ProtoWriter().apply {
    uint32(1, cmdType)
    string(2, cmd)
    uint32(3, seqNo)
    string(4, msgId)
    string(5, module)
    bool(6, needAck)
    meta.forEach { item ->
        message(7) {
            string(1, item.key)
            string(2, item.value)
        }
    }
    int32(10, status)
}.finish()

internal fun ConnMsg.encode(): ByteArray = ProtoWriter().apply {
    message(1) { writeRaw(head.encode()) }
    if (data.isNotEmpty()) bytes(2, data)
}.finish()

internal fun AuthBindReq.encode(): ByteArray = ProtoWriter().apply {
    string(1, bizId)
    message(2) {
        string(1, authInfo.uid)
        string(2, authInfo.source)
        string(3, authInfo.token)
    }
    message(3) {
        string(1, deviceInfo.appVersion)
        string(2, deviceInfo.appOperationSystem)
        string(10, deviceInfo.instanceId)
        string(24, deviceInfo.botVersion)
    }
}.finish()

internal fun SendC2CMessageReq.encode(): ByteArray = ProtoWriter().apply {
    string(1, msgId)
    string(2, toAccount)
    string(3, fromAccount)
    uint32(4, msgRandom)
    msgBody.forEach { element ->
        message(5) { writeRaw(element.encode()) }
    }
    string(6, groupCode)
}.finish()

internal fun SendGroupMessageReq.encode(): ByteArray = ProtoWriter().apply {
    string(1, msgId)
    string(2, groupCode)
    string(3, fromAccount)
    string(5, random)
    msgBody.forEach { element ->
        message(6) { writeRaw(element.encode()) }
    }
}.finish()

internal fun MsgBodyElement.encode(): ByteArray = ProtoWriter().apply {
    string(1, msgType)
    message(2) {
        string(1, msgContent.text)
        string(2, msgContent.uuid)
        string(4, msgContent.data)
        string(5, msgContent.desc)
        string(10, msgContent.url)
        string(12, msgContent.fileName)
    }
}.finish()

internal fun ConnMsg.Companion.decode(data: ByteArray): ConnMsg {
    var head = Head()
    var body = ByteArray(0)
    val reader = ProtoReader(data)
    while (reader.hasMore()) {
        val tag = reader.readTag()
        when (tag.field to tag.wire) {
            1 to 2 -> head = Head.decode(reader.readBytes())
            2 to 2 -> body = reader.readBytes()
            else -> reader.skip(tag.wire)
        }
    }
    return ConnMsg(head, body)
}

internal fun AuthBindRsp.Companion.decode(data: ByteArray): AuthBindRsp {
    var code = 0
    var message = ""
    var connectId = ""
    var timestamp = 0L
    var clientIp = ""
    val reader = ProtoReader(data)
    while (reader.hasMore()) {
        val tag = reader.readTag()
        when (tag.field to tag.wire) {
            1 to 0 -> code = reader.readInt32()
            2 to 2 -> message = reader.readString()
            3 to 2 -> connectId = reader.readString()
            4 to 0 -> timestamp = reader.readUInt64()
            5 to 2 -> clientIp = reader.readString()
            else -> reader.skip(tag.wire)
        }
    }
    return AuthBindRsp(code, message, connectId, timestamp, clientIp)
}

internal fun PingRsp.Companion.decode(data: ByteArray): PingRsp {
    var interval = 0
    var timestamp = 0L
    val reader = ProtoReader(data)
    while (reader.hasMore()) {
        val tag = reader.readTag()
        when (tag.field to tag.wire) {
            1 to 0 -> interval = reader.readUInt32()
            2 to 0 -> timestamp = reader.readUInt64()
            else -> reader.skip(tag.wire)
        }
    }
    return PingRsp(interval, timestamp)
}

internal fun PushMsg.Companion.decode(data: ByteArray): PushMsg {
    var cmd = ""
    var module = ""
    var msgId = ""
    var body = ByteArray(0)
    val reader = ProtoReader(data)
    while (reader.hasMore()) {
        val tag = reader.readTag()
        when (tag.field to tag.wire) {
            1 to 2 -> cmd = reader.readString()
            2 to 2 -> module = reader.readString()
            3 to 2 -> msgId = reader.readString()
            4 to 2 -> body = reader.readBytes()
            else -> reader.skip(tag.wire)
        }
    }
    return PushMsg(cmd, module, msgId, body)
}

internal fun DirectedPush.Companion.decode(data: ByteArray): DirectedPush {
    var type = 0
    var content = ""
    val reader = ProtoReader(data)
    while (reader.hasMore()) {
        val tag = reader.readTag()
        when (tag.field to tag.wire) {
            1 to 0 -> type = reader.readUInt32()
            2 to 2 -> content = reader.readString()
            else -> reader.skip(tag.wire)
        }
    }
    return DirectedPush(type, content)
}

internal fun KickoutMsg.Companion.decode(data: ByteArray): KickoutMsg {
    var status = 0
    var reason = ""
    var device = ""
    val reader = ProtoReader(data)
    while (reader.hasMore()) {
        val tag = reader.readTag()
        when (tag.field to tag.wire) {
            1 to 0 -> status = reader.readInt32()
            2 to 2 -> reason = reader.readString()
            3 to 2 -> device = reader.readString()
            else -> reader.skip(tag.wire)
        }
    }
    return KickoutMsg(status, reason, device)
}

internal fun InboundMessagePush.Companion.decode(data: ByteArray): InboundMessagePush {
    var callbackCommand = ""
    var fromAccount = ""
    var toAccount = ""
    var senderNickname = ""
    var groupId = ""
    var groupCode = ""
    var groupName = ""
    var msgSeq = 0L
    var msgRandom = 0L
    var msgTime = 0L
    var msgKey = ""
    var msgId = ""
    val msgBody = mutableListOf<MsgBodyElement>()
    var cloudCustomData = ""
    var eventTime = 0L
    var botOwnerId = ""
    var clawMsgType = 0
    var privateFromGroupCode = ""
    var traceId = ""
    val reader = ProtoReader(data)
    while (reader.hasMore()) {
        val tag = reader.readTag()
        when (tag.field to tag.wire) {
            1 to 2 -> callbackCommand = reader.readString()
            2 to 2 -> fromAccount = reader.readString()
            3 to 2 -> toAccount = reader.readString()
            4 to 2 -> senderNickname = reader.readString()
            5 to 2 -> groupId = reader.readString()
            6 to 2 -> groupCode = reader.readString()
            7 to 2 -> groupName = reader.readString()
            8 to 0 -> msgSeq = reader.readUInt32Long()
            9 to 0 -> msgRandom = reader.readUInt32Long()
            10 to 0 -> msgTime = reader.readUInt32Long()
            11 to 2 -> msgKey = reader.readString()
            12 to 2 -> msgId = reader.readString()
            13 to 2 -> msgBody.add(MsgBodyElement.decode(reader.readBytes()))
            14 to 2 -> cloudCustomData = reader.readString()
            15 to 0 -> eventTime = reader.readUInt32Long()
            16 to 2 -> botOwnerId = reader.readString()
            18 to 0 -> clawMsgType = reader.readInt32()
            19 to 2 -> privateFromGroupCode = reader.readString()
            20 to 2 -> traceId = LogInfoExt.decode(reader.readBytes()).traceId
            else -> reader.skip(tag.wire)
        }
    }
    return InboundMessagePush(
        callbackCommand = callbackCommand,
        fromAccount = fromAccount,
        toAccount = toAccount,
        senderNickname = senderNickname,
        groupId = groupId,
        groupCode = groupCode,
        groupName = groupName,
        msgSeq = msgSeq,
        msgRandom = msgRandom,
        msgTime = msgTime,
        msgKey = msgKey,
        msgId = msgId,
        msgBody = msgBody,
        cloudCustomData = cloudCustomData,
        eventTime = eventTime,
        botOwnerId = botOwnerId,
        clawMsgType = clawMsgType,
        privateFromGroupCode = privateFromGroupCode,
        traceId = traceId,
    )
}

internal fun SendMessageRsp.Companion.decode(data: ByteArray): SendMessageRsp {
    var code = 0
    var message = ""
    val reader = ProtoReader(data)
    while (reader.hasMore()) {
        val tag = reader.readTag()
        when (tag.field to tag.wire) {
            1 to 0 -> code = reader.readInt32()
            2 to 2 -> message = reader.readString()
            else -> reader.skip(tag.wire)
        }
    }
    return SendMessageRsp(code, message)
}

internal fun MsgBodyElement.Companion.decode(data: ByteArray): MsgBodyElement {
    var msgType = ""
    var content = MsgContent()
    val reader = ProtoReader(data)
    while (reader.hasMore()) {
        val tag = reader.readTag()
        when (tag.field to tag.wire) {
            1 to 2 -> msgType = reader.readString()
            2 to 2 -> content = MsgContent.decode(reader.readBytes())
            else -> reader.skip(tag.wire)
        }
    }
    return MsgBodyElement(msgType, content)
}

private fun MsgContent.Companion.decode(data: ByteArray): MsgContent {
    var text = ""
    var uuid = ""
    var body = ""
    var desc = ""
    var url = ""
    var fileName = ""
    val reader = ProtoReader(data)
    while (reader.hasMore()) {
        val tag = reader.readTag()
        when (tag.field to tag.wire) {
            1 to 2 -> text = reader.readString()
            2 to 2 -> uuid = reader.readString()
            4 to 2 -> body = reader.readString()
            5 to 2 -> desc = reader.readString()
            10 to 2 -> url = reader.readString()
            12 to 2 -> fileName = reader.readString()
            else -> reader.skip(tag.wire)
        }
    }
    return MsgContent(text, uuid, body, desc, url, fileName)
}

private data class LogInfoExt(val traceId: String = "") {
    companion object {
        fun decode(data: ByteArray): LogInfoExt {
            var traceId = ""
            val reader = ProtoReader(data)
            while (reader.hasMore()) {
                val tag = reader.readTag()
                when (tag.field to tag.wire) {
                    1 to 2 -> traceId = reader.readString()
                    else -> reader.skip(tag.wire)
                }
            }
            return LogInfoExt(traceId)
        }
    }
}

private fun Head.Companion.decode(data: ByteArray): Head {
    var cmdType = 0
    var cmd = ""
    var seqNo = 0
    var msgId = ""
    var module = ""
    var needAck = false
    var status = 0
    val meta = mutableListOf<HeadMeta>()
    val reader = ProtoReader(data)
    while (reader.hasMore()) {
        val tag = reader.readTag()
        when (tag.field to tag.wire) {
            1 to 0 -> cmdType = reader.readUInt32()
            2 to 2 -> cmd = reader.readString()
            3 to 0 -> seqNo = reader.readUInt32()
            4 to 2 -> msgId = reader.readString()
            5 to 2 -> module = reader.readString()
            6 to 0 -> needAck = reader.readBool()
            7 to 2 -> meta.add(HeadMeta.decode(reader.readBytes()))
            10 to 0 -> status = reader.readInt32()
            else -> reader.skip(tag.wire)
        }
    }
    return Head(cmdType, cmd, seqNo, msgId, module, needAck, status, meta)
}

private fun HeadMeta.Companion.decode(data: ByteArray): HeadMeta {
    var key = ""
    var value = ""
    val reader = ProtoReader(data)
    while (reader.hasMore()) {
        val tag = reader.readTag()
        when (tag.field to tag.wire) {
            1 to 2 -> key = reader.readString()
            2 to 2 -> value = reader.readString()
            else -> reader.skip(tag.wire)
        }
    }
    return HeadMeta(key, value)
}

internal class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun int32(field: Int, value: Int) {
        if (value == 0) return
        varint(field, value.toLong())
    }

    fun uint32(field: Int, value: Int) {
        if (value == 0) return
        varint(field, value.toLong() and 0xffffffffL)
    }

    fun bool(field: Int, value: Boolean) {
        if (!value) return
        varint(field, 1)
    }

    fun string(field: Int, value: String) {
        if (value.isEmpty()) return
        bytes(field, value.toByteArray(Charsets.UTF_8))
    }

    fun bytes(field: Int, value: ByteArray) {
        tag(field, 2)
        writeVarint(value.size.toLong())
        out.write(value)
    }

    fun message(field: Int, block: ProtoWriter.() -> Unit) {
        val nested = ProtoWriter().apply(block).finish()
        bytes(field, nested)
    }

    fun writeRaw(value: ByteArray) {
        out.write(value)
    }

    fun finish(): ByteArray = out.toByteArray()

    private fun varint(field: Int, value: Long) {
        tag(field, 0)
        writeVarint(value)
    }

    private fun tag(field: Int, wire: Int) {
        writeVarint(((field shl 3) or wire).toLong())
    }

    private fun writeVarint(value: Long) {
        var current = value
        while (true) {
            if ((current and 0x7fL.inv()) == 0L) {
                out.write(current.toInt())
                return
            }
            out.write(((current and 0x7fL) or 0x80L).toInt())
            current = current ushr 7
        }
    }
}

internal class ProtoReader(private val data: ByteArray) {
    private var position = 0

    fun hasMore(): Boolean = position < data.size

    fun readTag(): Tag {
        val value = readVarint().toInt()
        return Tag(value ushr 3, value and 7)
    }

    fun readInt32(): Int = readVarint().toInt()

    fun readUInt32(): Int = readVarint().toInt()

    fun readUInt32Long(): Long = readVarint() and 0xffffffffL

    fun readUInt64(): Long = readVarint()

    fun readBool(): Boolean = readVarint() != 0L

    fun readString(): String = String(readBytes(), Charsets.UTF_8)

    fun readBytes(): ByteArray {
        val length = readVarint().toInt()
        val end = position + length
        require(length >= 0 && end <= data.size)
        val result = data.copyOfRange(position, end)
        position = end
        return result
    }

    fun skip(wire: Int, depth: Int = 0) {
        when (wire) {
            0 -> readVarint()
            1 -> position += 8
            2 -> {
                val length = readVarint().toInt()
                position += length
            }
            3 -> skipGroup(depth + 1)
            4 -> Unit
            5 -> position += 4
            else -> position = data.size
        }
    }

    private fun skipGroup(depth: Int) {
        if (depth > MAX_GROUP_DEPTH) {
            position = data.size
            return
        }
        while (hasMore()) {
            val tag = readTag()
            if (tag.wire == 4) return
            skip(tag.wire, depth)
        }
    }

    private fun readVarint(): Long {
        var shift = 0
        var result = 0L
        while (true) {
            require(position < data.size)
            val byte = data[position].toInt() and 0xff
            position++
            result = result or ((byte and 0x7f).toLong() shl shift)
            if (byte and 0x80 == 0) return result
            shift += 7
            require(shift <= 63)
        }
    }

    data class Tag(val field: Int, val wire: Int)

    companion object {
        private const val MAX_GROUP_DEPTH = 16
    }
}
