#!/usr/bin/env python3

import os
import random
import sys

import praw


def main(args: [str]) -> None:
    client_id = os.environ["CLIENT_ID"]
    client_secret = os.environ["CLIENT_SECRET"]

    subreddit_name = args[0]

    client = praw.Reddit(
        client_id=client_id,
        client_secret=client_secret,
        user_agent="maiden by u/andoverite (https://github.com/musubii/maiden)"
    )

    subreddit = client.subreddit(subreddit_name)

    posts = []
    for post in subreddit.top("week", limit=64):
        if post.is_self:
            continue

        posts.append(post)

    random.shuffle(posts)

    result = None
    for post in posts:
        if result is not None:
            break

        # For images and videos
        if post.is_reddit_media_domain:
            if post.domain == "i.redd.it":
                result = post.url
            elif post.domain == "v.redd.it":
                # Assume this is v.redd.it for now
                result = post.media["reddit_video"]["fallback_url"]
            else:
                # TODO
                pass
        else:
            if post.domain in [
                "i.imgur.com",
                "imgur.com",
                "gfycat.com"
            ]:
                result = post.url
            else:
                # TODO: handle gallery links
                pass

    if result is not None:
        print(result)
    else:
        raise ValueError("Could not find any posts with readable media. Try again later!")


if __name__ == "__main__":
    main(sys.argv[1:])
