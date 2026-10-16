// Pass a Thrift browser runtime and a client constructor from createBindings().
// Each call owns its protocol buffers, so concurrent Vue requests cannot corrupt each other.
export function createRpcClient(Thrift, Client, endpoint, timeoutMillis = 15000) {
    return new Proxy({}, {
        get(target, method) {
            if (method === "then" || typeof method !== "string"
                    || typeof Client.prototype[method] !== "function"
                    || method.startsWith("send_") || method.startsWith("recv_")) {
                return undefined;
            }
            return async (...args) => {
                const transport = new FetchTransport(endpoint, timeoutMillis);
                const client = new Client(new Thrift.TJSONProtocol(transport));
                const result = await client[method](...args);
                // Thrift oneway methods resolve before their HTTP request finishes.
                await transport.completion;
                if (transport.failure) {
                    throw transport.failure;
                }
                return result;
            };
        }
    });
}

class FetchTransport {
    constructor(endpoint, timeoutMillis) {
        this.endpoint = endpoint;
        this.timeoutMillis = timeoutMillis;
        this.request = "";
        this.response = "";
        this.failure = null;
        this.completion = Promise.resolve();
    }

    write(value) {
        this.request = value;
    }

    readAll() {
        if (this.failure) {
            throw this.failure;
        }
        return this.response;
    }

    flush(async, callback) {
        if (!async) {
            throw new Error("Synchronous browser RPC is not supported");
        }
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), this.timeoutMillis);
        this.completion = fetch(this.endpoint, {
            method: "POST",
            headers: { "Content-Type": "application/vnd.apache.thrift.json" },
            body: this.request,
            cache: "no-store",
            credentials: "omit",
            redirect: "error",
            signal: controller.signal
        }).then(async response => {
            if (!response.ok) {
                throw new Error(`RPC HTTP ${response.status}`);
            }
            this.response = await response.text();
        }).catch(failure => {
            this.failure = failure;
        }).then(() => {
            clearTimeout(timer);
            if (callback) {
                callback();
            }
        });
    }
}
