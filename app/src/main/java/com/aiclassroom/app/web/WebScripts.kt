package com.aiclassroom.app.web

import org.json.JSONArray
import org.json.JSONObject

/**
 * 宿主自己拼的页面脚本。
 *
 * 这里是「不执行任意 JS」这条防线的落点：AI 只能传 fill / click / wait 三种结构化动作，
 * 脚本永远由本文件根据 selector + value 拼出来，裸 JavaScript 没有任何入口能进到 WebView。
 * 每个脚本都返回一个对象 / 数组（PageHandle.eval 拿到的就是它的 JSON），
 * 这样调用方可以结构化判断成败，而不是去猜字符串。
 *
 * 写法约定：先把要嵌进 JS 的字符串在 Kotlin 侧算好（[js] 转义成带引号的 JSON 字面量），
 * 再用 ${} 插值拼进脚本——不要在 raw string 里裸写 $变量，
 * 那会被 Kotlin 当成模板插值，而不是 JS 的一部分。
 */
internal object WebScripts {

    /** 把任意文本安全地嵌进 JS 字符串字面量（含引号转义）。 */
    private fun js(text: String): String = JSONObject.quote(text)

    /**
     * 探测一批 selector 的真实状态：存不存在、什么标签、input 类型、自身文案、所在表单的文案。
     * 敏感动作判定和密码框判定都吃这份结果，而且判定发生在真正操作之前。
     */
    fun probe(selectors: List<String>): String {
        val list = JSONArray().apply { selectors.forEach { put(it) } }.toString()
        return """
            (function(){
              var sels = ${list};
              var out = [];
              for (var i = 0; i < sels.length; i++) {
                var s = sels[i];
                var el = null;
                var invalid = false;
                try { el = document.querySelector(s); } catch (e) { invalid = true; }
                if (invalid) { out.push({selector: s, found: false, invalid: true}); continue; }
                if (!el) { out.push({selector: s, found: false}); continue; }
                var label = [
                  el.getAttribute('aria-label') || '',
                  el.getAttribute('title') || '',
                  el.getAttribute('name') || '',
                  el.getAttribute('placeholder') || '',
                  el.id || ''
                ].join(' ');
                var own = (el.innerText || el.value || '') + '';
                var form = el.closest ? el.closest('form') : null;
                out.push({
                  selector: s,
                  found: true,
                  tag: (el.tagName || '').toLowerCase(),
                  type: (el.getAttribute('type') || '').toLowerCase(),
                  text: own.slice(0, 160),
                  label: label.slice(0, 160),
                  formText: form ? (form.innerText || '').slice(0, 400) : '',
                  formAction: form ? (form.getAttribute('action') || '') : '',
                  disabled: !!el.disabled
                });
              }
              return out;
            })()
        """.trimIndent()
    }

    /** 填值。走原生 setter 再补 input/change 事件，React / Vue 那种受控组件才认。 */
    fun fill(selector: String, value: String): String {
        val sel = js(selector)
        val val_ = js(value)
        return """
            (function(){
              var el = null;
              try { el = document.querySelector(${sel}); } catch (e) { return {ok: false, error: 'selector 不合法'}; }
              if (!el) return {ok: false, error: '找不到元素'};
              if ((el.getAttribute('type') || '').toLowerCase() === 'password') {
                return {ok: false, error: 'password-field'};
              }
              var proto = el.tagName === 'TEXTAREA'
                ? window.HTMLTextAreaElement.prototype
                : window.HTMLInputElement.prototype;
              var setter = Object.getOwnPropertyDescriptor(proto, 'value');
              el.focus();
              if (setter && setter.set) { setter.set.call(el, ${val_}); } else { el.value = ${val_}; }
              el.dispatchEvent(new Event('input', {bubbles: true}));
              el.dispatchEvent(new Event('change', {bubbles: true}));
              return {ok: true, value: (el.value || '').slice(0, 80)};
            })()
        """.trimIndent()
    }

    /**
     * 执行一段用户可见页面里的 JS，用于 `web_act` 的 `js` 动作。
     *
     * 典型用途是控制媒体播放：
     * ```
     * document.querySelector('video').currentTime = 83
     * ```
     * 这样就能精确 seek 到某一秒再截图，而不必靠点进度条碰运气。
     *
     * 包一层立即执行函数：避免脚本里的变量泄漏到页面全局作用域，
     * 也保证返回值永远是一个可 JSON 化的对象（PageHandle.eval 只认这个）。
     */
    fun evaluate(code: String): String = """
        (function(){
          try {
            var __v = (function(){ """ + code + """ })();
            return {ok: true, value: __v === undefined ? null : String(__v)};
          } catch (e) {
            return {ok: false, error: String((e && e.message) || e)};
          }
        })()
    """.trimIndent()

    /** 点一下。用原生 click()，不模拟坐标，避免点到浮层上。 */
    fun click(selector: String): String {
        val sel = js(selector)
        return """
            (function(){
              var el = null;
              try { el = document.querySelector(${sel}); } catch (e) { return {ok: false, error: 'selector 不合法'}; }
              if (!el) return {ok: false, error: '找不到元素'};
              if (el.disabled) return {ok: false, error: '元素处于 disabled 状态'};
              el.scrollIntoView({block: 'center'});
              el.click();
              return {ok: true, tag: (el.tagName || '').toLowerCase()};
            })()
        """.trimIndent()
    }

    /** 页面标题 + 正文开头，回填给模型当作「点完之后发生了什么」的粗略判断依据。 */
    fun pageSummary(): String = """
        (function(){
          return {
            title: (document.title || '').slice(0, 120),
            url: (location.href || '').slice(0, 300),
            text: ((document.body && document.body.innerText) || '').replace(/\s+/g, ' ').slice(0, 1200)
          };
        })()
    """.trimIndent()
}
