import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import vm from "node:vm";
import { afterEach, describe, test } from "node:test";
import { createRpcClient } from "../../main/browser/client.mjs";
import { createBindings } from "../../../build/generated/browser-module/bindings.mjs";

// Supply the official matching browser runtime; it is not vendored into this repository.
const runtimePath = process.env.THRIFT_BROWSER_RUNTIME;
if (!runtimePath) {
    throw new Error("Set THRIFT_BROWSER_RUNTIME to Apache Thrift 0.25.0 lib/js/src/thrift.js");
}
const context = vm.createContext({});
vm.runInContext(await readFile(runtimePath, "utf8"), context);
const bindings = createBindings(context.Thrift, undefined);
const originalFetch = globalThis.fetch;

afterEach(() => {
    globalThis.fetch = originalFetch;
});

function ui(timeout = 1000) {
    return createRpcClient(context.Thrift, bindings.UiServiceClient, "/rpc/ui", timeout);
}

function areasReply(name) {
    return new Response(JSON.stringify([1, "areas", 2, 0, {
        "0": { lst: ["rec", 1, { "1": { str: name }, "2": { i64: 3 } }] }
    }]));
}

describe("RPC browser client", () => {
    test("parallel generated calls have independent buffers", async () => {
        globalThis.fetch = async (url, request) => {
            const slow = request.body.includes("slow-token");
            await new Promise(resolve => setTimeout(resolve, slow ? 20 : 1));
            return areasReply(slow ? "slow.area" : "fast.area");
        };
        const client = ui();
        const [slow, fast] = await Promise.all([
            client.areas("slow-token"), client.areas("fast-token")
        ]);
        assert.equal(slow[0].name, "slow.area");
        assert.equal(fast[0].name, "fast.area");
    });
    
    test("typed Thrift errors reject the generated Promise", async () => {
        globalThis.fetch = async () => new Response(JSON.stringify([1, "areas", 2, 0, {
            "1": { rec: { "1": { str: "UNAUTHORIZED" }, "2": { str: "expired" } } }
        }]));
        await assert.rejects(ui().areas("expired"), error => error.code === "UNAUTHORIZED");
    });
    
    test("HTTP and network failures reject instead of leaving a pending Promise", async () => {
        globalThis.fetch = async () => new Response("", { status: 403 });
        await assert.rejects(ui().areas("token"), /RPC HTTP 403/);
        globalThis.fetch = async () => { throw new Error("connection refused"); };
        await assert.rejects(ui().areas("token"), /connection refused/);
    });
    
    test("request timeout aborts fetch and rejects the call", async () => {
        globalThis.fetch = (url, request) => new Promise((resolve, reject) => {
            request.signal.addEventListener("abort", () => reject(new Error("aborted")));
        });
        await assert.rejects(ui(10).areas("token"), /aborted/);
    });
    
    test("oneway logout waits for HTTP completion and propagates HTTP failure", async () => {
        let finish;
        globalThis.fetch = () => new Promise(resolve => { finish = resolve; });
        const login = createRpcClient(context.Thrift, bindings.LoginServiceClient, "/rpc/login");
        let completed = false;
        const pending = login.logout("token").then(() => { completed = true; });
        await new Promise(resolve => setTimeout(resolve, 5));
        assert.equal(completed, false);
        finish(new Response(""));
        await pending;
        assert.equal(completed, true);
        globalThis.fetch = async () => new Response("", { status: 503 });
        await assert.rejects(login.logout("token"), /RPC HTTP 503/);
    });
});
