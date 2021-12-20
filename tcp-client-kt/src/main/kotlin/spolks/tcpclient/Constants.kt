package spolks.tcpclient

const val OK = "ok"
const val ERROR = "error"
const val CONTINUE = "continue"
const val STOP = "stop"
const val DEFAULT_SEGMENT_SIZE = 32_000
const val TCP_SEGMENT_SIZE = 500
const val RESOURCES_FOLDER = "resources"

const val UDP_PACKET_SIZE = 512
const val UDP_CLIENT_ID_SIZE = 4
const val UDP_QUEUE_CAPACITY = 4

// const val UDP_DEFAULT_SO_TIMEOUT = 10_000
const val UDP_DEFAULT_SO_TIMEOUT = 10_000_000
const val UDP_UPLOAD_SO_TIMEOUT = 10
const val UDP_MIN_WINDOW = 1
const val UDP_MAX_WINDOW = 32
const val UDP_MAX_WAIT_FAILS = 40
