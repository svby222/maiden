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
        "https://www.youtube.com/watch?v=8r3aIV5iS88",
        "https://www.youtube.com/watch?v=tm-y_rUVJ1U",
        "https://www.youtube.com/watch?v=5S_HKj5PqzU",
        "https://www.youtube.com/watch?v=BPvAAOJi4Nk",
        "https://www.youtube.com/watch?v=B9jcx3kVdW0",
        "https://www.youtube.com/watch?v=-DVmNrX8dZU",
        "https://www.youtube.com/watch?v=WNLfOSfDM9c",
        "https://www.youtube.com/watch?v=b_A0RL3lQTA",
        "https://www.youtube.com/watch?v=9vK0_IZ6ZQc",
        "https://www.youtube.com/watch?v=rV-2H_xiO2Y",
        "https://www.youtube.com/watch?v=0WDjcn1TrC4",
        "https://www.youtube.com/watch?v=1iZ08ng-Gyg",
        "https://www.youtube.com/watch?v=oU46YIaLmRI",
        "https://www.youtube.com/watch?v=8wokhQAjQDM",
        "https://www.youtube.com/watch?v=qi7UOQAHYLU",
        "https://www.youtube.com/watch?v=36bBTlPNv2w",
        "https://www.youtube.com/watch?v=EX2wFR5rVOg",
        "https://www.youtube.com/watch?v=L8UO06D3buU",
        "https://www.youtube.com/watch?v=uDkVIkXF5Zk",
        "https://www.youtube.com/watch?v=JdLMWKTCcKY",
        "https://www.youtube.com/watch?v=nqDNzu9U6i4",
        "https://www.youtube.com/watch?v=3L6x3up2uF0",
        "https://www.youtube.com/watch?v=D7hyoU6Wqmw",
        "https://www.youtube.com/watch?v=uwTbr1vw31A",
    )

    @Command
    @HelpText("wuewuewuewuewuewue")
    suspend fun gumi(context: CommandContext) {
        context.replyAsync(GUMI_VIDEOS.random())
    }
}
