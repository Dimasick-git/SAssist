package dev.ryazha.sassist.script

import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject

/**
 * Runs lightweight third-party JS snippets in an embedded Rhino sandbox.
 * Android can't JIT, so we force interpreted mode (optimizationLevel = -1).
 * Scripts get a global `sa` object: sa.log(x), sa.send(text), sa.lastMessage().
 */
object ScriptEngine {
    fun run(code: String, host: ScriptHost): String {
        val cx = Context.enter()
        cx.optimizationLevel = -1
        cx.languageVersion = Context.VERSION_ES6
        return try {
            val scope = cx.initStandardObjects()
            ScriptableObject.putProperty(scope, "sa", Context.javaToJS(host, scope))
            val result = cx.evaluateString(scope, code, "sassist-script", 1, null)
            val tail = if (result != null && result != Context.getUndefinedValue())
                "\n=> " + Context.toString(result) else ""
            host.output() + tail
        } catch (e: Throwable) {
            host.output() + "\n!! " + (e.message ?: e.toString())
        } finally {
            Context.exit()
        }
    }
}
