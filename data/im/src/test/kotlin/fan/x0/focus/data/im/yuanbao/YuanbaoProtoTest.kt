package fan.x0.focus.data.im.yuanbao

import fan.x0.focus.data.im.ImChunker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YuanbaoProtoTest {
    @Test
    fun connMessageRoundTrip() {
        val auth = AuthBindReq(
            authInfo = AuthInfo(uid = "bot-1", source = "bot", token = "token-1"),
            deviceInfo = DeviceInfo(appVersion = "1.0", botVersion = "1.0"),
        ).encode()
        val original = ConnMsg(
            head = Head(
                cmdType = 0,
                cmd = "auth-bind",
                seqNo = 7,
                msgId = "message-1",
                module = "conn_access",
                needAck = true,
                meta = listOf(HeadMeta("route", "test")),
            ),
            data = auth,
        )
        val decoded = ConnMsg.decode(original.encode())
        assertEquals("auth-bind", decoded.head.cmd)
        assertEquals("message-1", decoded.head.msgId)
        assertEquals("conn_access", decoded.head.module)
        assertEquals(7, decoded.head.seqNo)
        assertTrue(decoded.head.needAck)
        assertEquals("route", decoded.head.meta.first().key)
        assertEquals("test", decoded.head.meta.first().value)
        assertTrue(auth.contentEquals(decoded.data))
    }

    @Test
    fun inboundGroupMessageDecodes() {
        val content = ProtoWriter().apply { string(1, "hello group") }.finish()
        val element = ProtoWriter().apply {
            string(1, "TIMTextElem")
            bytes(2, content)
        }.finish()
        val payload = ProtoWriter().apply {
            string(2, "user-1")
            string(4, "Alice")
            string(6, "group-1")
            string(7, "Team")
            uint32(8, 42)
            uint32(10, 1_700_000_000)
            string(12, "msg-1")
            bytes(13, element)
            string(16, "owner-1")
        }.finish()
        val message = InboundMessagePush.decode(payload)
        assertEquals("user-1", message.fromAccount)
        assertEquals("Alice", message.senderNickname)
        assertEquals("group-1", message.groupCode)
        assertEquals("Team", message.groupName)
        assertEquals("msg-1", message.msgId)
        assertEquals("owner-1", message.botOwnerId)
        assertEquals(1, message.msgBody.size)
        assertEquals("TIMTextElem", message.msgBody.first().msgType)
        assertEquals("hello group", message.msgBody.first().msgContent.text)
    }

    @Test
    fun outboundC2CRequestEncodes() {
        val request = SendC2CMessageReq(
            msgId = "message-2",
            toAccount = "user-1",
            fromAccount = "bot-1",
            msgRandom = 99,
            msgBody = listOf(
                MsgBodyElement(
                    msgType = "TIMTextElem",
                    msgContent = MsgContent(text = "hello"),
                ),
            ),
        )
        val reader = ProtoReader(request.encode())
        var to = ""
        var from = ""
        var text = ""
        while (reader.hasMore()) {
            val tag = reader.readTag()
            when (tag.field) {
                2 -> to = reader.readString()
                3 -> from = reader.readString()
                5 -> text = MsgBodyElement.decode(reader.readBytes()).msgContent.text
                else -> reader.skip(tag.wire)
            }
        }
        assertEquals("user-1", to)
        assertEquals("bot-1", from)
        assertEquals("hello", text)
    }

    @Test
    fun wrappedAndDirectedPushesDoNotMisdecode() {
        val content = ProtoWriter().apply { string(1, "hello") }.finish()
        val element = ProtoWriter().apply {
            string(1, "TIMTextElem")
            bytes(2, content)
        }.finish()
        val inboundBytes = ProtoWriter().apply {
            string(2, "user-1")
            string(4, "Alice")
            string(12, "msg-1")
            bytes(13, element)
        }.finish()
        val pushBytes = ProtoWriter().apply {
            string(1, "push")
            string(2, "yuanbao_openclaw_proxy")
            string(3, "head-1")
            bytes(4, inboundBytes)
        }.finish()
        assertTrue(InboundMessagePush.decode(pushBytes).msgBody.isEmpty())
        val push = PushMsg.decode(pushBytes)
        assertEquals("push", push.cmd)
        val wrapped = InboundMessagePush.decode(push.data)
        assertEquals("user-1", wrapped.fromAccount)
        assertEquals("hello", wrapped.msgBody.first().msgContent.text)

        val directedBytes = ProtoWriter().apply {
            uint32(1, 3)
            string(
                2,
                "{\"from_account\":\"user-1\",\"msg_body\":[{\"msg_type\":\"TIMTextElem\",\"msg_content\":{\"text\":\"hello\"}}]}",
            )
        }.finish()
        assertTrue(InboundMessagePush.decode(directedBytes).msgBody.isEmpty())
        val directed = DirectedPush.decode(directedBytes)
        assertEquals(3, directed.type)
        assertTrue(directed.content.contains("from_account"))
    }

    @Test
    fun jsonPushDecodes() {
        val content = """
            {
              "callback_command": "C2C.CallbackAfterSendMsg",
              "from_account": "user-1",
              "to_account": "bot-1",
              "sender_nickname": "呵呵",
              "msg_seq": 1663167412,
              "msg_time": 1793833686,
              "msg_key": "key-1",
              "msg_id": "id-1",
              "msg_body": [
                {
                  "msg_type": "TIMTextElem",
                  "msg_content": {
                    "text": "测试"
                  }
                }
              ],
              "bot_owner_id": "owner-1",
              "claw_msg_type": 2,
              "log_ext": {
                "trace_id": "trace-1"
              }
            }
        """.trimIndent()
        val message = parseJsonMessage(content)!!
        assertEquals("user-1", message.fromAccount)
        assertEquals("bot-1", message.toAccount)
        assertEquals("呵呵", message.senderNickname)
        assertEquals("C2C.CallbackAfterSendMsg", message.callbackCommand)
        assertEquals("id-1", message.msgId)
        assertEquals("测试", message.msgBody.first().msgContent.text)
        assertEquals("owner-1", message.botOwnerId)
        assertEquals("trace-1", message.traceId)
    }

    @Test
    fun chunkerSplitsLongCodeBlock() {
        val text = buildString {
            appendLine("intro")
            appendLine()
            appendLine("```kotlin")
            repeat(200) { appendLine("val value$it = $it") }
            appendLine("```")
            appendLine()
            appendLine("outro")
        }
        val chunks = ImChunker.split(text, 300)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 300 })
        assertTrue(chunks.any { it.contains("```kotlin") })
        assertTrue(chunks.any { it.contains("```") })
    }
}
