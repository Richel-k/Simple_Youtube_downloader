const baseUrl = "http://localhost:8080/api";

// Appelle le point d'accès -F pour lister les formats
function getFormats() {
    const url = document.getElementById('url').value;
    if (!url) return alert("Veuillez saisir un lien YouTube.");

    const display = document.getElementById('formats-display');
    display.style.display = "block";
    display.innerText = "Exécution de yt-dlp -F en cours... Veuillez patienter...";

    fetch(`${baseUrl}/formats?url=${encodeURIComponent(url)}`)
        .then(response => response.text())
        .then(data => {
            display.innerText = data;
            document.getElementById('download-section').style.display = "block";
        })
        .catch(err => {
            display.innerText = "Erreur lors de la récupération des formats : " + err;
        });
}

// Lance le téléchargement asynchrone via l'ID sélectionné
function startDownload() {
    const url = encodeURIComponent(document.getElementById('url').value);
    const path = encodeURIComponent(document.getElementById('path').value);
    const formatId = encodeURIComponent(document.getElementById('formatId').value);

    if (!formatId) return alert("Veuillez saisir un ID de format.");

    const progressBar = document.getElementById('progressBar');
    const statusMessage = document.getElementById('status-message');

    statusMessage.innerText = "Connexion au flux de téléchargement...";
    progressBar.style.width = "0%";
    progressBar.innerText = "0%";

    // Écoute du flux SSE
    const eventSource = new EventSource(`${baseUrl}/download?url=${url}&path=${path}&formatId=${formatId}`);
    // Richel Kembou Fosso. .  .
    eventSource.addEventListener('progress', function (event) {
        const data = JSON.parse(event.data);
        progressBar.style.width = data.percent + "%";
        progressBar.innerText = data.percent + "%";

        document.getElementById('totalSize').innerText = data.totalSize;
        document.getElementById('speed').innerText = data.speed;
        document.getElementById('eta').innerText = data.eta;
        statusMessage.innerText = "Téléchargement en cours...";
    });

    eventSource.addEventListener('status', function (event) {
        statusMessage.innerText = event.data; // Affiche les lignes de logs secondaires
    });

    eventSource.addEventListener('complete', function (event) {
        statusMessage.innerHTML = `<strong style="color: green;">${event.data}</strong>`;
        eventSource.close();
    });

    eventSource.addEventListener('error', function (event) {
        statusMessage.innerHTML = `<strong style="color: red;">Téléchargement interrompu ou finalisé.</strong>`;
        eventSource.close();
    });
}