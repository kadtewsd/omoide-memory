(function() {
    // 1. セレクションの有効化
    const style = document.createElement('style');
    style.textContent = `
        * {
            user-select: text !important;
            -webkit-user-select: text !important;
            cursor: auto !important;
        }
    `;
    document.head.appendChild(style);

    // 2. 蓄積用バッファ
    window.finalResults = [];
    window.uniqueKeys = new Set();

    window.onKeyDownHandler = function(e) {
        if (e.key.toLowerCase() === 'q') {
            const selectedText = window.getSelection().toString();
            if (!selectedText) return;

            const lines = selectedText.split(/\n/)
                                     .map(l => l.trim())
                                     .filter(l => l !== "" && l !== "Reply" && l !== "Save" && !l.startsWith("Seen by") && !l.includes("Duration:"));

            let currentName = "";
            let currentComments = [];
            let addedCount = 0;

            for (let line of lines) {
                const isName = line.includes('·') || /(.+)\s[·・]\s([A-Z][a-z]{2}|[0-9]{4})/.test(line);
                if (isName) {
                    if (savePair(currentName, currentComments)) addedCount++;
                    currentName = line;
                    currentComments = [];
                } else if (currentName) {
                    currentComments.push(line);
                }
            }
            if (savePair(currentName, currentComments)) addedCount++;

            console.log(`%c蓄積中: ${window.finalResults.length} 件`, "color: #1a73e8; font-weight: bold;");
        }
    };

    function savePair(name, commentArray) {
        if (!name || commentArray.length === 0) return false;
        const comment = commentArray.join(' ').trim();
        const key = name + comment;
        if (!window.uniqueKeys.has(key)) {
            window.uniqueKeys.add(key);
            window.finalResults.push(`\t${comment}\t${name}`);
            return true;
        }
        return false;
    }

    window.removeEventListener('keydown', window.onKeyDownHandler);
    window.addEventListener('keydown', window.onKeyDownHandler);

    // 3. コピー & パージ
    window.finish = function() {
        if (window.finalResults.length === 0) {
            console.warn("蓄積されたデータがありません。");
            return;
        }

        // ヘッダーなしで結合
        const tsv = window.finalResults.join('\n');
        copy(tsv);

        const count = window.finalResults.length;

        // --- パージ処理 ---
        window.finalResults = [];
        window.uniqueKeys = new Set();
        // ------------------

        console.log(`%c✅ ${count}件をコピーし、リストをリセットしました。`, "color: #34a853; font-weight: bold;");
        alert(count + "件コピー完了。次の作業のためにリストを空にしました。");
    };

    console.log("🚀 Ready! 『Q』で蓄積、 『finish()』でコピー＆パージします。");
})();
