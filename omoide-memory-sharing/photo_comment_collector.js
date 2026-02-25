(function() {

    // 1. セレクションを有効化
    const style = document.createElement('style');
    style.textContent = `
        * {
            user-select: text !important;
            -webkit-user-select: text !important;
            cursor: auto !important;
        }
    `;
    document.head.appendChild(style);

    // 2. Qで即コピー
    async function onKeyDownHandler(e) {
        if (e.key.toLowerCase() !== 'q') return;

        const selectedText = window.getSelection().toString();
        if (!selectedText) {
            console.warn("⚠️ 選択テキストがありません。");
            return;
        }

        const lines = selectedText.split(/\n/)
            .map(l => l.trim())
            .filter(l =>
                l !== "" &&
                l !== "Reply" &&
                l !== "Save" &&
                !l.startsWith("Seen by") &&
                !l.includes("Duration:")
            );

        let results = [];
        let uniqueKeys = new Set();

        let currentName = "";
        let currentComments = [];

        for (let line of lines) {
            const isName =
                line.includes('·') ||
                /(.+)\s[·・]\s([A-Z][a-z]{2}|[0-9]{4})/.test(line);

            if (isName) {
                savePair();
                currentName = line;
                currentComments = [];
            } else if (currentName) {
                currentComments.push(line);
            }
        }

        savePair();

        function savePair() {
            if (!currentName || currentComments.length === 0) return;

            const comment = currentComments.join(' ').trim();
            const key = currentName + comment;

            if (!uniqueKeys.has(key)) {
                uniqueKeys.add(key);
                results.push(`\t${comment}\t${currentName}`);
            }
        }

        if (results.length === 0) {
            console.warn("⚠️ 有効なコメントが見つかりませんでした。");
            return;
        }

        const tsv = results.join('\n');

        try {
            await navigator.clipboard.writeText(tsv);
            console.log(`✅ ${results.length}件をコピーしました。`);
        } catch (err) {
            console.error("❌ コピー失敗:", err);
        }
    }

    window.removeEventListener('keydown', onKeyDownHandler);
    window.addEventListener('keydown', onKeyDownHandler);

    console.log("🚀 Ready! 『Q』で即コピーします（蓄積なし）。");
})();
