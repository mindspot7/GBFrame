package gb.frame

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text as MaterialText
import java.util.Locale

private val portugueseKitText = mapOf(
    "Achievements" to "Conquistas",
    "About" to "Sobre",
    "Achievements" to "Conquistas",
    "Add" to "Adicionar",
    "Audio" to "Áudio",
    "Auto" to "Automático",
    "Auto frame skip" to "Pular quadros automaticamente",
    "Back" to "Voltar",
    "Bios folder" to "Pasta da BIOS",
    "Display" to "Tela",
    "Display mode" to "Modo de exibição",
    "Fullscreen" to "Tela cheia",
    "4:3" to "4:3",
    "16:9" to "16:9",
    "On" to "Ligado",
    "Off" to "Desligado",
    "Enabled" to "Ativado",
    "Disabled" to "Desativado",
    "Fast" to "Rápido",
    "Accurate" to "Preciso",
    "Default" to "Padrão",
    "Recommended" to "Recomendado",
    "Cancel" to "Cancelar",
    "Cheats" to "Códigos",
    "Choose files" to "Escolher arquivos",
    "Choose folder" to "Escolher pasta",
    "Clear" to "Limpar",
    "Close" to "Fechar",
    "Close app" to "Fechar aplicativo",
    "Close search" to "Fechar busca",
    "Controls" to "Controles",
    "Cover Image" to "Capa do jogo",
    "Data folder" to "Pasta de dados",
    "Data folder needs attention" to "A pasta de dados precisa de atenção",
    "Done" to "Concluído",
    "Exit" to "Sair",
    "Extras" to "Extras",
    "Fast forward" to "Avanço rápido",
    "Filter" to "Filtro",
    "Game" to "Jogo",
    "Load state" to "Carregar estado",
    "Local Search" to "Busca local",
    "Name" to "Nome",
    "Network" to "Rede",
    "Not now" to "Agora não",
    "Not played" to "Não jogado",
    "Not set" to "Não definido",
    "OK" to "OK",
    "Opening game…" to "Abrindo jogo…",
    "Extracting archive…" to "Extraindo arquivo compactado…",
    "The first opening of a large archive can take a few minutes. The screen will stay on while it loads." to "A primeira abertura de um arquivo compactado grande pode levar alguns minutos. A tela ficará ligada durante o carregamento.",
    "Password" to "Senha",
    "Performance" to "Desempenho",
    "Privacy policy" to "Política de privacidade",
    "Read the privacy policy" to "Ler a política de privacidade",
    "Renderer" to "Renderizador",
    "Resolution" to "Resolução",
    "Reset" to "Reiniciar",
    "Reset to defaults" to "Restaurar padrões",
    "Restore purchase" to "Restaurar compra",
    "Resume" to "Continuar",
    "Retry" to "Tentar novamente",
    "SAVED" to "SALVO",
    "Save state" to "Salvar estado",
    "Save states" to "Estados salvos",
    "Set" to "Definir",
    "Shader" to "Shader",
    "Skip" to "Pular",
    "Start" to "Iniciar",
    "System" to "Sistema",
    "Theme" to "Tema",
    "Video" to "Vídeo",
    "Volume" to "Volume",
    "Web Search" to "Busca na internet",
    "Widescreen hack" to "Hack widescreen",
    "Empty" to "Vazio",
    "Filter" to "Filtro",
    "Loading" to "Carregando",
    "Load state" to "Carregar estado",
    "Save state" to "Salvar estado",
    "Slot" to "Slot",
    "Resume" to "Continuar",
    "Exit" to "Sair",
    "Fast forward" to "Avanço rápido",
    "Swap disc" to "Trocar disco",
    "Disc tray" to "Bandeja de disco",
    "Reset" to "Reiniciar",
    "Quick menu" to "Menu rápido",
    "More options above" to "Mais opções acima",
    "More options below" to "Mais opções abaixo",
    "Renderer" to "Renderizador",
    "Resolution" to "Resolução",
    "Enhanced resolution" to "Resolução aprimorada",
    "Video" to "Vídeo",
    "Performance" to "Desempenho",
    "Loading times hacks" to "Aceleração de carregamento",
    "Recommended settings per game" to "Configurações recomendadas por jogo",
    "Theme" to "Tema",
    "About" to "Sobre",
    "Audio" to "Áudio",
    "System" to "Sistema",
    "Extras" to "Extras",
    "Controls" to "Controles",
    "Resolution" to "Resolução",
    "Filter" to "Filtro",
    "Shader" to "Shader",
    "Widescreen hack" to "Hack widescreen",
    "Cheats" to "Códigos",
    "Achievements" to "Conquistas",
    "Swap disc" to "Trocar disco",
    "Reset" to "Reiniciar",
    "Volume" to "Volume",
    "Show" to "Exibir",
    "Hidden" to "Oculto",
    "English" to "Inglês",
    "Português (Brasil)" to "Português (Brasil)",
    "Español (Latinoamérica)" to "Espanhol (América Latina)",
    "Entry" to "Baixo",
    "Low" to "Baixo",
    "Mid" to "Médio",
    "High" to "Alto",
    "Graphics detected" to "Gráficos detectados",
    "Save state" to "Salvar estado",
    "Load state" to "Carregar estado",
    "OK" to "OK",
    "Yes" to "Sim",
    "No" to "Não",
    "Username" to "Usuário",
    "Version \$version" to "Versão \$version",
    "YOUR PROFILE" to "SEU PERFIL",
    "RECOMMENDED SETTINGS" to "CONFIGURAÇÕES RECOMENDADAS",
    "Apply settings" to "Aplicar configurações",
    "Settings applied" to "Configurações aplicadas",
    "The recommended settings are in use. You can change them later in Settings." to "As configurações recomendadas estão em uso. Você pode alterá-las depois em Configurações.",
    "Without applying, the app starts with the default settings: OpenGL, 1x, no shader, linear filter and auto frame skip off." to "Sem aplicar, o app inicia com as configurações padrão: OpenGL, 1x, sem shader, filtro linear e pular quadros automaticamente desligado.",
    "GBFRAME SETUP" to "CONFIGURAÇÃO GBFRAME",
    "Themes unlocked" to "Temas liberados",
    "Gold themes" to "Temas Gold",
    "Support the developer" to "Apoie o desenvolvedor",
    "Support & unlock" to "Apoiar e desbloquear",
    "Support and unlock" to "Apoiar e desbloquear",
    "ONE-TIME PURCHASE" to "COMPRA ÚNICA",
    "Google Play purchase is unavailable in this build." to "A compra pelo Google Play não está disponível nesta versão.",
    "Could not connect to Google Play. Try again later." to "Não foi possível conectar ao Google Play. Tente novamente mais tarde.",
    "Checking purchases for this Google Play account…" to "Verificando as compras desta conta do Google Play…",
    "Check for updates" to "Verificar atualizações",
    "Checking…" to "Verificando…",
    "Check" to "Verificar",
    "Update available" to "Atualização disponível",
    "Update" to "Atualizar",
    "Open Google Play" to "Abrir Google Play",
    "Later" to "Mais tarde",
    "Update not available" to "Atualização indisponível",
    "Release page" to "Página da versão",
    "No release found" to "Nenhuma versão publicada foi encontrada.",
    "Up to date" to "Atualizado",
    "You have the latest version." to "Você está usando a versão mais recente.",
    "Could not check for updates. Check your connection and try again." to "Não foi possível verificar atualizações. Confira sua conexão e tente novamente.",
    "Google Play could not be reached" to "Não foi possível acessar o Google Play.",
    "The release has no APK" to "Esta versão não contém um APK.",
    "Allow installing from this app, then try again" to "Permita instalações por este app e tente novamente.",
    "SHA-256 does not match; update rejected" to "O SHA-256 não confere; a atualização foi recusada.",
    "The downloaded file is not a valid APK" to "O arquivo baixado não é um APK válido.",
    "The APK is for a different app; update rejected" to "O APK pertence a outro app; a atualização foi recusada.",
    "The APK signature does not match this app; update rejected" to "A assinatura do APK não corresponde a este app; a atualização foi recusada.",
    "Could not start the update. Try again later." to "Não foi possível iniciar a atualização. Tente novamente mais tarde.",
    "Google Play could not start the update. Try again later." to "O Google Play não conseguiu iniciar a atualização. Tente novamente mais tarde.",
    "An update is available, but Google Play cannot apply it right now." to "Há uma atualização disponível, mas o Google Play não pode aplicá-la agora.",
    "Could not load the purchase from Google Play." to "Não foi possível carregar a compra do Google Play.",
    "This app's one-time product is not available in Google Play yet." to "O produto de compra única deste app ainda não está disponível no Google Play.",
    "No eligible purchase offer is available for this Google Play account." to "Nenhuma oferta de compra elegível está disponível para esta conta do Google Play.",
    "Could not check Google Play purchases. Try again later." to "Não foi possível verificar as compras do Google Play. Tente novamente mais tarde.",
    "No Gold purchase was found for this Google Play account." to "Nenhuma compra PRO foi encontrada nesta conta do Google Play.",
    "Google Play could not complete the purchase. Try again later." to "O Google Play não conseguiu concluir a compra. Tente novamente mais tarde.",
    "The purchase is pending. It unlocks after Google Play confirms payment." to "A compra está pendente. Ela é liberada depois que o Google Play confirmar o pagamento.",
    "Checking your Gold license…" to "Verificando sua licença PRO…",
    "Gold themes restored. Thanks for supporting development." to "Temas PRO restaurados. Obrigado por apoiar o desenvolvimento.",
    "Thanks for supporting development. All themes are unlocked." to "Obrigado por apoiar o desenvolvimento. Todos os temas estão liberados.",
    "This purchase was canceled or refunded. Gold themes remain locked." to "Esta compra foi cancelada ou reembolsada. Os temas PRO continuam bloqueados.",
    "Google Play could not confirm this purchase. Gold themes remain locked." to "O Google Play não conseguiu confirmar esta compra. Os temas PRO continuam bloqueados.",
    "No verified Gold license was found for this Google Play account." to "Nenhuma licença PRO verificada foi encontrada para esta conta do Google Play.",
    "Google Play could not verify the Gold purchase. Try again or restore the purchase." to "O Google Play não conseguiu verificar a compra PRO. Tente novamente ou restaure a compra.",
    "The license server is unavailable. Previously unlocked themes remain available." to "O servidor de licenças está indisponível. Os temas já liberados continuam disponíveis.",
    "Could not reach the license server. Connect to the internet and try again." to "Não foi possível acessar o servidor de licenças. Conecte-se à internet e tente novamente.",
    "The purchase is not available right now. Try again later." to "A compra não está disponível agora. Tente novamente mais tarde.",
    "Google Play could not start the purchase. Try again later." to "O Google Play não conseguiu iniciar a compra. Tente novamente mais tarde.",
    "Done" to "Concluído",
    "Use image" to "Usar imagem",
    "Use this cover?" to "Usar esta capa?",
    "Tap a cover to select it, or hold an image. Then choose Use image." to "Toque em uma capa para selecioná-la ou mantenha uma imagem pressionada. Depois escolha Usar imagem.",
    "Tap a button on the pad or in the list, then press the button on your controller." to "Toque em um botão do controle na tela ou na lista e pressione o botão correspondente no controle físico.",
    "Press the button on your controller you want to use for it…" to "Pressione no controle físico o botão que deseja usar…",
    "Bind one button, or hold several together (a combination) and release. Buttons in a combination are not sent to the game." to "Configure um botão ou mantenha vários pressionados juntos para criar uma combinação. Os botões da combinação não serão enviados ao jogo.",
    "No cheats for this game yet. Add a code or import a supported cheat file." to "Este jogo ainda não tem códigos. Adicione um código ou importe um arquivo compatível.",
    "Import BIOS files into the app's active data folder. The location shown in System is where the emulator reads them." to "Importe os arquivos de BIOS para a pasta de dados ativa do aplicativo. O emulador lê os arquivos no local indicado em Sistema.",
    "Auto-tuned for the best performance this device can sustain." to "Ajustado automaticamente para o melhor desempenho que este dispositivo consegue manter.",
    "The data folder was saved. Close the app now, then open it again to use the selected folder directly. Existing destination files were preserved." to "A pasta de dados foi salva. Feche o aplicativo e abra-o novamente para usar diretamente a pasta selecionada. Os arquivos já existentes no destino foram preservados.",
    "Moving to the selected data folder…" to "Movendo para a pasta de dados selecionada…",
    "Reopen the app" to "Reabrir o aplicativo",
    "Close search" to "Fechar busca",
    "Local Search" to "Busca no dispositivo",
    "Choose files" to "Escolher arquivos",
    "Choose folder" to "Escolher pasta",
    "Check access" to "Verificar acesso",
    "Back" to "Voltar",
    "Widescreen" to "Widescreen",
    "Extended screen" to "Tela estendida",
    "Quick menu" to "Menu rápido",
    "Fast-forward audio" to "Áudio no avanço rápido",
    "Settings" to "Configurações",
    "Loading time hacks" to "Aceleração de carregamento",
    "More" to "Mais",
    "Search" to "Buscar",
    "Search games" to "Buscar jogos",
    "All games" to "Todos os jogos",
    "Favorites" to "Favoritos",
    "Recently played" to "Jogos recentes",
    "Play time" to "Tempo de jogo",
    "Game library" to "Biblioteca de jogos",
    "No games found" to "Nenhum jogo encontrado",
    "No games yet" to "Ainda não há jogos",
    "Open" to "Abrir",
    "Import" to "Importar",
    "Select" to "Selecionar",
    "Apply" to "Aplicar",
    "Restore" to "Restaurar",
    "Next" to "Avançar",
    "Previous" to "Anterior",
    "Loading…" to "Carregando…",
    "Choose" to "Escolher",
    "Choose image" to "Escolher imagem",
    "Choose a folder" to "Escolher uma pasta",
    "No image selected" to "Nenhuma imagem selecionada",
    "Restore default" to "Restaurar padrão",
    "Default theme" to "Tema padrão",
    "System settings" to "Configurações do sistema",
    "Hardcore mode" to "Modo Hardcore",
    "Offline achievements" to "Conquistas offline",
    "Logging in…" to "Entrando…",
    "Challenge active" to "Desafio ativo",
    "Game mastered" to "Jogo dominado",
    "Achievement unlocked" to "Conquista desbloqueada",
    "No achievements available for this game (or it was not recognized)." to "Não há conquistas disponíveis para este jogo ou ele não foi reconhecido.",
    "Enable RetroAchievements" to "Ativar RetroAchievements",
    "Earn achievements while playing. You need a RetroAchievements account and an internet connection; this does not affect emulation speed." to "Conquiste objetivos durante o jogo. É necessária uma conta RetroAchievements e conexão com a internet. Isso não afeta a velocidade da emulação.",
    "Enables stricter achievement rules and extra points. Cheats and save states are disabled; turning it on during a game resets the game." to "Ativa regras mais rígidas e pontos extras. Códigos e estados salvos ficam desativados; ativar durante um jogo reinicia a partida.",
    "Log in to RetroAchievements in Settings to earn achievements." to "Entre na sua conta RetroAchievements em Configurações para conquistar objetivos.",
)

private val spanishKitText = mapOf(
    "Achievements" to "Logros",
    "About" to "Acerca de",
    "Achievements" to "Logros",
    "Add" to "Añadir",
    "Audio" to "Audio",
    "Auto" to "Automático",
    "Auto frame skip" to "Salto de fotogramas automático",
    "Back" to "Volver",
    "Bios folder" to "Carpeta de BIOS",
    "Display" to "Pantalla",
    "Display mode" to "Modo de pantalla",
    "Fullscreen" to "Pantalla completa",
    "4:3" to "4:3",
    "16:9" to "16:9",
    "On" to "Activado",
    "Off" to "Desactivado",
    "Enabled" to "Activado",
    "Disabled" to "Desactivado",
    "Fast" to "Rápido",
    "Accurate" to "Preciso",
    "Default" to "Predeterminado",
    "Recommended" to "Recomendado",
    "Cancel" to "Cancelar",
    "Cheats" to "Trucos",
    "Choose files" to "Elegir archivos",
    "Choose folder" to "Elegir carpeta",
    "Clear" to "Borrar",
    "Close" to "Cerrar",
    "Close app" to "Cerrar aplicación",
    "Close search" to "Cerrar búsqueda",
    "Controls" to "Controles",
    "Cover Image" to "Carátula del juego",
    "Data folder" to "Carpeta de datos",
    "Data folder needs attention" to "La carpeta de datos requiere atención",
    "Done" to "Listo",
    "Exit" to "Salir",
    "Extras" to "Extras",
    "Fast forward" to "Avance rápido",
    "Filter" to "Filtro",
    "Game" to "Juego",
    "Load state" to "Cargar estado",
    "Local Search" to "Búsqueda local",
    "Name" to "Nombre",
    "Network" to "Red",
    "Not now" to "Ahora no",
    "Not played" to "Sin jugar",
    "Not set" to "Sin asignar",
    "OK" to "Aceptar",
    "Opening game…" to "Abriendo juego…",
    "Extracting archive…" to "Extrayendo archivo comprimido…",
    "The first opening of a large archive can take a few minutes. The screen will stay on while it loads." to "La primera apertura de un archivo comprimido grande puede tardar unos minutos. La pantalla permanecerá encendida durante la carga.",
    "Password" to "Contraseña",
    "Performance" to "Rendimiento",
    "Privacy policy" to "Política de privacidad",
    "Read the privacy policy" to "Leer la política de privacidad",
    "Renderer" to "Renderizador",
    "Resolution" to "Resolución",
    "Reset" to "Reiniciar",
    "Reset to defaults" to "Restablecer valores predeterminados",
    "Restore purchase" to "Restaurar compra",
    "Resume" to "Continuar",
    "Retry" to "Reintentar",
    "SAVED" to "GUARDADO",
    "Save state" to "Guardar estado",
    "Save states" to "Estados guardados",
    "Set" to "Asignar",
    "Shader" to "Shader",
    "Skip" to "Omitir",
    "Start" to "Iniciar",
    "System" to "Sistema",
    "Theme" to "Tema",
    "Video" to "Video",
    "Volume" to "Volumen",
    "Web Search" to "Búsqueda web",
    "Widescreen hack" to "Hack de pantalla ancha",
    "Empty" to "Vacío",
    "Loading" to "Cargando",
    "Slot" to "Ranura",
    "Resume" to "Continuar",
    "Exit" to "Salir",
    "Fast forward" to "Avance rápido",
    "Swap disc" to "Cambiar disco",
    "Disc tray" to "Bandeja de disco",
    "Reset" to "Reiniciar",
    "Quick menu" to "Menú rápido",
    "More options above" to "Más opciones arriba",
    "More options below" to "Más opciones abajo",
    "Enhanced resolution" to "Resolución mejorada",
    "Loading times hacks" to "Aceleración de carga",
    "Recommended settings per game" to "Ajustes recomendados por juego",
    "Show" to "Mostrar",
    "Hidden" to "Oculto",
    "English" to "English",
    "Português (Brasil)" to "Português (Brasil)",
    "Español (Latinoamérica)" to "Español (Latinoamérica)",
    "Entry" to "Bajo",
    "Low" to "Bajo",
    "Mid" to "Medio",
    "High" to "Alto",
    "Graphics detected" to "Gráficos detectados",
    "Yes" to "Sí",
    "No" to "No",
    "Username" to "Usuario",
    "Version \$version" to "Versión \$version",
    "YOUR PROFILE" to "TU PERFIL",
    "RECOMMENDED SETTINGS" to "AJUSTES RECOMENDADOS",
    "Apply settings" to "Aplicar ajustes",
    "Settings applied" to "Ajustes aplicados",
    "The recommended settings are in use. You can change them later in Settings." to "Los ajustes recomendados están en uso. Puedes cambiarlos después en Ajustes.",
    "Without applying, the app starts with the default settings: OpenGL, 1x, no shader, linear filter and auto frame skip off." to "Sin aplicar, la app inicia con los ajustes predeterminados: OpenGL, 1x, sin shader, filtro lineal y salto automático de fotogramas desactivado.",
    "GBFRAME SETUP" to "CONFIGURACIÓN DE GBFRAME",
    "Themes unlocked" to "Temas desbloqueados",
    "Gold themes" to "Temas Gold",
    "Support the developer" to "Apoya al desarrollador",
    "Support & unlock" to "Apoyar y desbloquear",
    "Support and unlock" to "Apoyar y desbloquear",
    "ONE-TIME PURCHASE" to "COMPRA ÚNICA",
    "Google Play purchase is unavailable in this build." to "La compra en Google Play no está disponible en esta versión.",
    "Could not connect to Google Play. Try again later." to "No se pudo conectar con Google Play. Inténtalo de nuevo más tarde.",
    "Checking purchases for this Google Play account…" to "Comprobando las compras de esta cuenta de Google Play…",
    "Check for updates" to "Buscar actualizaciones",
    "Checking…" to "Comprobando…",
    "Check" to "Buscar",
    "Update available" to "Actualización disponible",
    "Update" to "Actualizar",
    "Open Google Play" to "Abrir Google Play",
    "Later" to "Más tarde",
    "Update not available" to "Actualización no disponible",
    "Release page" to "Página de la versión",
    "No release found" to "No se encontró ninguna versión publicada.",
    "Up to date" to "Actualizado",
    "You have the latest version." to "Tienes la versión más reciente.",
    "Could not check for updates. Check your connection and try again." to "No se pudieron buscar actualizaciones. Comprueba tu conexión e inténtalo de nuevo.",
    "Google Play could not be reached" to "No se pudo acceder a Google Play.",
    "The release has no APK" to "Esta versión no incluye un APK.",
    "Allow installing from this app, then try again" to "Permite las instalaciones desde esta app e inténtalo de nuevo.",
    "SHA-256 does not match; update rejected" to "El SHA-256 no coincide; se rechazó la actualización.",
    "The downloaded file is not a valid APK" to "El archivo descargado no es un APK válido.",
    "The APK is for a different app; update rejected" to "El APK pertenece a otra app; se rechazó la actualización.",
    "The APK signature does not match this app; update rejected" to "La firma del APK no coincide con esta app; se rechazó la actualización.",
    "Could not start the update. Try again later." to "No se pudo iniciar la actualización. Inténtalo de nuevo más tarde.",
    "Google Play could not start the update. Try again later." to "Google Play no pudo iniciar la actualización. Inténtalo de nuevo más tarde.",
    "An update is available, but Google Play cannot apply it right now." to "Hay una actualización disponible, pero Google Play no puede aplicarla ahora.",
    "Could not load the purchase from Google Play." to "No se pudo cargar la compra desde Google Play.",
    "This app's one-time product is not available in Google Play yet." to "El producto de pago único de esta app aún no está disponible en Google Play.",
    "No eligible purchase offer is available for this Google Play account." to "No hay ninguna oferta de compra válida disponible para esta cuenta de Google Play.",
    "Could not check Google Play purchases. Try again later." to "No se pudieron comprobar las compras de Google Play. Inténtalo de nuevo más tarde.",
    "No Gold purchase was found for this Google Play account." to "No se encontró ninguna compra PRO en esta cuenta de Google Play.",
    "Google Play could not complete the purchase. Try again later." to "Google Play no pudo completar la compra. Inténtalo de nuevo más tarde.",
    "The purchase is pending. It unlocks after Google Play confirms payment." to "La compra está pendiente. Se desbloquea cuando Google Play confirme el pago.",
    "Checking your Gold license…" to "Comprobando tu licencia PRO…",
    "Gold themes restored. Thanks for supporting development." to "Temas PRO restaurados. Gracias por apoyar el desarrollo.",
    "Thanks for supporting development. All themes are unlocked." to "Gracias por apoyar el desarrollo. Todos los temas están desbloqueados.",
    "This purchase was canceled or refunded. Gold themes remain locked." to "Esta compra fue cancelada o reembolsada. Los temas PRO siguen bloqueados.",
    "Google Play could not confirm this purchase. Gold themes remain locked." to "Google Play no pudo confirmar esta compra. Los temas PRO siguen bloqueados.",
    "No verified Gold license was found for this Google Play account." to "No se encontró ninguna licencia PRO verificada para esta cuenta de Google Play.",
    "Google Play could not verify the Gold purchase. Try again or restore the purchase." to "Google Play no pudo verificar la compra PRO. Inténtalo de nuevo o restaura la compra.",
    "The license server is unavailable. Previously unlocked themes remain available." to "El servidor de licencias no está disponible. Los temas ya desbloqueados siguen disponibles.",
    "Could not reach the license server. Connect to the internet and try again." to "No se pudo acceder al servidor de licencias. Conéctate a internet e inténtalo de nuevo.",
    "The purchase is not available right now. Try again later." to "La compra no está disponible ahora. Inténtalo de nuevo más tarde.",
    "Google Play could not start the purchase. Try again later." to "Google Play no pudo iniciar la compra. Inténtalo de nuevo más tarde.",
    "Use image" to "Usar imagen",
    "Use this cover?" to "¿Usar esta carátula?",
    "Tap a cover to select it, or hold an image. Then choose Use image." to "Toca una carátula para seleccionarla o mantén pulsada una imagen. Luego elige Usar imagen.",
    "Tap a button on the pad or in the list, then press the button on your controller." to "Toca un botón del mando en pantalla o de la lista y pulsa el botón correspondiente en tu control físico.",
    "Press the button on your controller you want to use for it…" to "Pulsa en tu control físico el botón que quieras asignar…",
    "Bind one button, or hold several together (a combination) and release. Buttons in a combination are not sent to the game." to "Asigna un botón o mantén varios pulsados a la vez para crear una combinación. Esos botones no se enviarán al juego.",
    "No cheats for this game yet. Add a code or import a supported cheat file." to "Este juego aún no tiene trucos. Añade un código o importa un archivo compatible.",
    "Import BIOS files into the app's active data folder. The location shown in System is where the emulator reads them." to "Importa los archivos BIOS a la carpeta de datos activa de la aplicación. El emulador los lee desde la ubicación indicada en Sistema.",
    "Auto-tuned for the best performance this device can sustain." to "Ajustado automáticamente para ofrecer el mejor rendimiento sostenido de este dispositivo.",
    "The data folder was saved. Close the app now, then open it again to use the selected folder directly. Existing destination files were preserved." to "Se guardó la carpeta de datos. Cierra la aplicación y vuelve a abrirla para usar directamente la carpeta elegida. Se conservaron los archivos que ya había en el destino.",
    "Moving to the selected data folder…" to "Moviendo a la carpeta de datos seleccionada…",
    "Reopen the app" to "Volver a abrir la aplicación",
    "Check access" to "Comprobar acceso",
    "Extended screen" to "Pantalla extendida",
    "Quick menu" to "Menú rápido",
    "Fast-forward audio" to "Audio en avance rápido",
    "Settings" to "Ajustes",
    "Loading time hacks" to "Aceleración de carga",
    "More" to "Más",
    "Search" to "Buscar",
    "Search games" to "Buscar juegos",
    "All games" to "Todos los juegos",
    "Favorites" to "Favoritos",
    "Recently played" to "Jugados recientemente",
    "Play time" to "Tiempo de juego",
    "Game library" to "Biblioteca de juegos",
    "No games found" to "No se encontraron juegos",
    "No games yet" to "Aún no hay juegos",
    "Open" to "Abrir",
    "Import" to "Importar",
    "Select" to "Seleccionar",
    "Apply" to "Aplicar",
    "Restore" to "Restaurar",
    "Next" to "Siguiente",
    "Previous" to "Anterior",
    "Loading…" to "Cargando…",
    "Choose" to "Elegir",
    "Choose image" to "Elegir imagen",
    "Choose a folder" to "Elegir una carpeta",
    "No image selected" to "No hay imagen seleccionada",
    "Restore default" to "Restaurar valor predeterminado",
    "Default theme" to "Tema predeterminado",
    "System settings" to "Ajustes del sistema",
    "Hardcore mode" to "Modo Hardcore",
    "Offline achievements" to "Logros sin conexión",
    "Logging in…" to "Iniciando sesión…",
    "Challenge active" to "Desafío activo",
    "Game mastered" to "Juego completado al 100 %",
    "Achievement unlocked" to "Logro desbloqueado",
    "No achievements available for this game (or it was not recognized)." to "No hay logros disponibles para este juego o no se pudo reconocer.",
    "Enable RetroAchievements" to "Activar RetroAchievements",
    "Earn achievements while playing. You need a RetroAchievements account and an internet connection; this does not affect emulation speed." to "Consigue logros mientras juegas. Necesitas una cuenta de RetroAchievements y conexión a internet. No afecta la velocidad de emulación.",
    "Enables stricter achievement rules and extra points. Cheats and save states are disabled; turning it on during a game resets the game." to "Activa reglas más estrictas y puntos adicionales. Se desactivan los trucos y los estados guardados; activarlo durante una partida reinicia el juego.",
    "Log in to RetroAchievements in Settings to earn achievements." to "Inicia sesión en RetroAchievements desde Ajustes para conseguir logros.",
)

private val completePortugueseKitText = portugueseKitText + extraPortugueseKitText
private val completeSpanishKitText = spanishKitText + extraSpanishKitText

/** Optional language override for surfaces such as first-run setup that must follow the device locale. */
internal val LocalKitLanguageOverride = compositionLocalOf<String?> { null }
private val textPlaceholder = Regex("\\$(?:\\{[^}]+\\}|[A-Za-z_][A-Za-z0-9_]*)")
private data class TextTemplate(val pattern: Regex, val targetTokens: List<String>, val translated: String)

private fun compileTextTemplates(translations: Map<String, String>): List<TextTemplate> = translations.mapNotNull { (source, target) ->
    val placeholders = textPlaceholder.findAll(source).toList()
    if (placeholders.isEmpty()) return@mapNotNull null
    val pattern = buildString {
        append('^')
        var cursor = 0
        placeholders.forEach { token ->
            append(Regex.escape(source.substring(cursor, token.range.first)))
            append("(.*?)")
            cursor = token.range.last + 1
        }
        append(Regex.escape(source.substring(cursor)))
        append('$')
    }.toRegex()
    val targetTokens = textPlaceholder.findAll(target).map { it.value }.toList()
    if (targetTokens.size != placeholders.size) return@mapNotNull null
    TextTemplate(pattern, targetTokens, target)
}.sortedByDescending { it.pattern.pattern.length }

private val portugueseTextTemplates = compileTextTemplates(completePortugueseKitText)
private val spanishTextTemplates = compileTextTemplates(completeSpanishKitText)

@Composable
private fun localizedKitText(text: String): String {
    val tag = LocalKitLanguageOverride.current ?: LocalConfiguration.current.locales[0]?.toLanguageTag().orEmpty()
    return translatedAppText(text, tag)
}

/** Localize a GBFrame/app toast or snackbar using the same dictionary as Compose labels. */
fun localizeAppText(context: android.content.Context, text: String): String =
    translatedAppText(text, AppLanguage.currentTag(context))

private fun translatedAppText(text: String, tag: String): String {
    val translations = when {
        tag.startsWith("pt", ignoreCase = true) -> completePortugueseKitText
        tag.startsWith("es", ignoreCase = true) -> completeSpanishKitText
        else -> emptyMap()
    }
    val templates = when {
        tag.startsWith("pt", ignoreCase = true) -> portugueseTextTemplates
        tag.startsWith("es", ignoreCase = true) -> spanishTextTemplates
        else -> emptyList()
    }
    translations[text]?.let { return it }
    for (template in templates) {
        val match = template.pattern.matchEntire(text) ?: continue
        var result = template.translated
        template.targetTokens.forEachIndexed { index, token ->
            result = result.replaceFirst(token, match.groupValues[index + 1])
        }
        return result
    }
    if (text.startsWith("Current version ")) return when {
        tag.startsWith("pt", ignoreCase = true) -> "Versão atual ${text.removePrefix("Current version ")}"
        tag.startsWith("es", ignoreCase = true) -> "Versión actual ${text.removePrefix("Current version ")}"
        else -> text
    }
    if (text.startsWith("Version ") && text.endsWith(" is available.")) {
        val version = text.removePrefix("Version ").removeSuffix(" is available.").removePrefix("build ")
        return when {
            tag.startsWith("pt", ignoreCase = true) -> "A versão $version está disponível."
            tag.startsWith("es", ignoreCase = true) -> "La versión $version está disponible."
            else -> text
        }
    }
    Regex("^Download failed \\(HTTP (\\d+)\\)$").matchEntire(text)?.let { match ->
        return when {
            tag.startsWith("pt", ignoreCase = true) -> "Falha no download (HTTP ${match.groupValues[1]})."
            tag.startsWith("es", ignoreCase = true) -> "Error de descarga (HTTP ${match.groupValues[1]})."
            else -> text
        }
    }
    if (text.equals("low", ignoreCase = true) || text.equals("entry", ignoreCase = true)) return translations["Low"] ?: text
    if (text.equals("mid", ignoreCase = true)) return translations["Mid"] ?: text
    if (text.equals("high", ignoreCase = true)) return translations["High"] ?: text
    Regex("^(entry|low|mid|high) device · (.*)$", RegexOption.IGNORE_CASE).matchEntire(text)?.let { match ->
        val tierKey = when (match.groupValues[1].lowercase()) {
            "entry", "low" -> "Low"
            "mid" -> "Mid"
            else -> "High"
        }
        val tier = translations[tierKey] ?: match.groupValues[1]
        val graphics = translations["Graphics detected"] ?: match.groupValues[2]
        return when {
            tag.startsWith("pt", ignoreCase = true) -> "Dispositivo $tier · $graphics"
            tag.startsWith("es", ignoreCase = true) -> "Dispositivo $tier · $graphics"
            else -> text
        }
    }
    if (text.startsWith("Version ")) return translations["Version \$version"]?.replace("\$version", text.removePrefix("Version ")) ?: text
    if (text.startsWith("Progress: ")) return when {
        tag.startsWith("pt", ignoreCase = true) -> "Progresso: ${text.removePrefix("Progress: ")}"
        tag.startsWith("es", ignoreCase = true) -> "Progreso: ${text.removePrefix("Progress: ")}"
        else -> text
    }
    if (text.startsWith("Data folder: ")) return when {
        tag.startsWith("pt", ignoreCase = true) -> "Pasta de dados: ${text.removePrefix("Data folder: ")}"
        tag.startsWith("es", ignoreCase = true) -> "Carpeta de datos: ${text.removePrefix("Data folder: ")}"
        else -> text
    }
    if (text.startsWith("Textures: ")) return when {
        tag.startsWith("pt", ignoreCase = true) -> "Texturas: ${text.removePrefix("Textures: ")}"
        tag.startsWith("es", ignoreCase = true) -> "Texturas: ${text.removePrefix("Textures: ")}"
        else -> text
    }
    if (text.startsWith("Set ")) return when {
        tag.startsWith("pt", ignoreCase = true) -> "Definir ${text.removePrefix("Set ")}"
        tag.startsWith("es", ignoreCase = true) -> "Asignar ${text.removePrefix("Set ")}"
        else -> text
    }
    if (text.startsWith("Remap ")) return when {
        tag.startsWith("pt", ignoreCase = true) -> "Remapear ${text.removePrefix("Remap ")}"
        tag.startsWith("es", ignoreCase = true) -> "Remapear ${text.removePrefix("Remap ")}"
        else -> text
    }
    if (text.contains(" unlocked · ") && text.contains(" points")) return when {
        tag.startsWith("pt", ignoreCase = true) -> text.replace(" unlocked · ", " desbloqueadas · ").replace(" points", " pontos")
        tag.startsWith("es", ignoreCase = true) -> text.replace(" unlocked · ", " desbloqueados · ").replace(" points", " puntos")
        else -> text
    }
    if (text.endsWith(" pts")) return when {
        tag.startsWith("pt", ignoreCase = true) -> text.removeSuffix(" pts") + " pontos"
        tag.startsWith("es", ignoreCase = true) -> text.removeSuffix(" pts") + " puntos"
        else -> text
    }
    val oneTime = "Make a one-time purchase to support development and unlock all themes in "
    if (text.startsWith(oneTime)) {
        val app = text.removePrefix(oneTime).removeSuffix(".")
        return when {
            tag.startsWith("pt", ignoreCase = true) -> "Faça uma compra única para apoiar o desenvolvimento e desbloquear todos os temas no $app."
            tag.startsWith("es", ignoreCase = true) -> "Haz una compra única para apoyar el desarrollo y desbloquear todos los temas en $app."
            else -> text
        }
    }
    val thanks = "Thanks for supporting development. All themes are unlocked in "
    if (text.startsWith(thanks)) {
        val app = text.removePrefix(thanks).removeSuffix(".")
        return when {
            tag.startsWith("pt", ignoreCase = true) -> "Obrigado por apoiar o desenvolvimento. Todos os temas estão liberados no $app."
            tag.startsWith("es", ignoreCase = true) -> "Gracias por apoyar el desarrollo. Todos los temas están desbloqueados en $app."
            else -> text
        }
    }
    if (text.startsWith("Support · ")) return when {
        tag.startsWith("pt", ignoreCase = true) -> "Apoiar · " + text.removePrefix("Support · ")
        tag.startsWith("es", ignoreCase = true) -> "Apoyar · " + text.removePrefix("Support · ")
        else -> text
    }
    if (text == "Unassigned") return when {
        tag.startsWith("pt", ignoreCase = true) -> "Sem atribuição"
        tag.startsWith("es", ignoreCase = true) -> "Sin asignar"
        else -> text
    }
    return text
}

/** Text primitive used by GBFrame screens so their UI follows the selected app language. */
@Composable
fun KitText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
    uppercase: Boolean = false,
) {
    val localizedText = localizedKitText(text)
    MaterialText(
        text = if (uppercase) localizedText.uppercase(Locale.ROOT) else localizedText, modifier = modifier, color = color,
        fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
        fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration,
        textAlign = textAlign, lineHeight = lineHeight, overflow = overflow, softWrap = softWrap,
        maxLines = maxLines, minLines = minLines, onTextLayout = onTextLayout, style = style,
    )
}

@Composable
fun KitText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
) {
    MaterialText(
        text = text, modifier = modifier, color = color, fontSize = fontSize,
        fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily,
        letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
        lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines,
        minLines = minLines, inlineContent = inlineContent, onTextLayout = onTextLayout, style = style,
    )
}
