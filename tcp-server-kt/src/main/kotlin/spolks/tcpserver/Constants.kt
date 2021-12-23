package spolks.tcpserver

const val OK = "ok"
const val ERROR = "error"
const val CONTINUE = "continue"
const val STOP = "stop"
const val RESOURCES_FOLDER = "resources"

const val UDP_PACKET_SIZE = 512
const val UDP_NUMBER_SIZE = 4
const val UDP_CLIENT_ID_SIZE = 4
const val UDP_QUEUE_CAPACITY = 4
const val UDP_MIN_WINDOW = 1
const val UDP_MAX_WINDOW = 16
const val UDP_MAX_WAIT_FAILS = 2_000
const val UDP_DEFAULT_SO_TIMEOUT = 10_000
const val UDP_DOWNLOAD_SO_TIMEOUT = 10
