export type MessageType = 'CONNECT' | 'CHAT' | 'ACK' | 'PING' | 'PONG' | 'STATUS'

export interface ChatMessage {
  type: MessageType
  id?: number
  senderId: number
  targetId: number
  isGroup: boolean
  content: string
  timestamp: number
}

class ChatSocket {
  private ws: WebSocket | null = null
  private url: string = 'ws://localhost:9090/ws'
  private heartbeatInterval: any = null
  private userId: number = 0
  private seenMessageIds: Set<number> = new Set()
  private seenMessageOrder: number[] = []
  private readonly maxSeenMessageIds: number = 2000
  
  public onMessageCallback: (msg: ChatMessage) => void = () => {}

  connect(userId: number) {
    if (this.ws) {
      this.ws.close()
    }
    this.userId = userId
    this.ws = new WebSocket(this.url)
    
    this.ws.onopen = () => {
      console.log('Connected to WebSocket')
      this.send({
        type: 'CONNECT',
        senderId: this.userId,
        targetId: 0,
        isGroup: false,
        content: 'Login',
        timestamp: Date.now()
      })
      this.startHeartbeat()
    }

    this.ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data) as ChatMessage
        if (msg.type === 'PONG') return
        if (msg.type === 'CHAT' && msg.id) {
          const isDuplicate = this.seenMessageIds.has(msg.id)
          this.sendAck(msg)
          if (isDuplicate) return
          this.rememberMessageId(msg.id)
        }
        this.onMessageCallback(msg)
      } catch (e) {
        console.error('Parse error', e)
      }
    }

    this.ws.onclose = () => {
      console.log('Disconnected. Reconnecting...')
      this.stopHeartbeat()
      setTimeout(() => this.connect(this.userId), 3000)
    }
  }

  send(msg: ChatMessage) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(msg))
    } else {
      console.warn('WS not open')
    }
  }

  private sendAck(msg: ChatMessage) {
    this.send({
      type: 'ACK',
      id: msg.id,
      senderId: this.userId,
      targetId: msg.senderId,
      isGroup: msg.isGroup,
      content: '',
      timestamp: Date.now()
    })
  }

  startHeartbeat() {
    this.heartbeatInterval = setInterval(() => {
      this.send({
        type: 'PING',
        senderId: this.userId,
        targetId: 0,
        isGroup: false,
        content: '',
        timestamp: Date.now()
      })
    }, 10000)
  }

  stopHeartbeat() {
    if (this.heartbeatInterval) clearInterval(this.heartbeatInterval)
  }

  disconnect() {
    if (this.ws) {
      this.ws.close()
      this.ws = null
    }
    this.stopHeartbeat()
    this.userId = 0
  }

  private rememberMessageId(id: number) {
    this.seenMessageIds.add(id)
    this.seenMessageOrder.push(id)

    if (this.seenMessageOrder.length > this.maxSeenMessageIds) {
      const evicted = this.seenMessageOrder.shift()
      if (evicted !== undefined) {
        this.seenMessageIds.delete(evicted)
      }
    }
  }
}

export const chatSocket = new ChatSocket()
