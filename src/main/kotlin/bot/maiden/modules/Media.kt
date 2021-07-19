package bot.maiden.modules

import bot.maiden.Command
import bot.maiden.CommandContext
import bot.maiden.HelpText
import bot.maiden.Module

object Media : Module {
    private val GUMI_VIDEOS = listOf(
        "https://www.youtube.com/watch?v=Je9nsIfAAVU",
        "https://www.youtube.com/watch?v=c40aKr8hAuU",
        "https://www.youtube.com/watch?v=eAuPhCSz0hI",
        "https://www.youtube.com/watch?v=R1P5KGm39SA",
        "https://www.youtube.com/watch?v=3_tt8jN2NQo",
        "https://www.youtube.com/watch?v=oDXvmsNWWXg",
        "https://www.youtube.com/watch?v=JAkuEpphuOY",
        "https://www.youtube.com/watch?v=DFVLatLg5Xw",
        "https://www.youtube.com/watch?v=f1_nbYATPIw",
        "https://www.youtube.com/watch?v=kg6nWLzH_Ks",
        "https://www.youtube.com/watch?v=CDc7k9LSNTk",
        "https://www.youtube.com/watch?v=KX4wF_hIC_o",
        "https://www.youtube.com/watch?v=ZuzNVI-1FIk",
        "https://www.youtube.com/watch?v=s0Favp8oRKc",
        "https://www.youtube.com/watch?v=yThaARXn76M",
        "https://www.youtube.com/watch?v=q6EO04WL7hA",
        "https://www.youtube.com/watch?v=UFgbexHuEYI",
        "https://www.youtube.com/watch?v=ewf-8rx9_uQ",
        "https://www.youtube.com/watch?v=kUJw2eVYznw",
        "https://www.youtube.com/watch?v=7NOGvQDPazI",
        "https://www.youtube.com/watch?v=SSv6UJ8QWys",
        "https://www.youtube.com/watch?v=BK-oRB-anlE",
        "https://www.youtube.com/watch?v=Y5g9mWkraus",
        "https://www.youtube.com/watch?v=1X2Kc-avqB8",
        "https://www.youtube.com/watch?v=Zf9cUw4KsGo",
        "https://www.youtube.com/watch?v=CerMaCRf9Ew",
        "https://www.youtube.com/watch?v=AktwOsRAg9s",
        "https://www.youtube.com/watch?v=KdRn3hbNrfI",
        "https://www.youtube.com/watch?v=FV5lVPPyDU0",
        "https://www.youtube.com/watch?v=hFdtscxJoIg",
        "https://www.youtube.com/watch?v=VtlEwODlClg",
        "https://www.youtube.com/watch?v=zpoEjahjUyE",
        "https://www.youtube.com/watch?v=UkiYyECm2uQ",
        "https://www.youtube.com/watch?v=WPNUQsLYkn0",
        "https://www.youtube.com/watch?v=Yr1JyfJBIUY",
        "https://www.youtube.com/watch?v=bmaj5toz_08",
        "https://www.youtube.com/watch?v=EEKpuo3jH0E",
    )

    @Command
    @HelpText("wuewuewuewuewuewue")
    suspend fun gumi(context: CommandContext) {
        context.replyAsync(GUMI_VIDEOS.random())
    }
}
