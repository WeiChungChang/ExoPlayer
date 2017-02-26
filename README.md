# ExoPlayer trick playback feature#

Per the [request][] of change playback rate (so called trick playback), here a trial to provide such functionality is launched,

[request]: https://github.com/google/ExoPlayer/issues/26

## Progress ##

1. [ Feb 19, 2017]: Provide the patch for trick playback when playing adaptive streaming content; as: https://github.com/WeiChungChang/ExoPlayer/commit/5f441187588aa9f5b2b16934bd7fce356155c839 
2. [Feb 21, 2017]: Propose the gereral solution for high speed & reverse playback funcationality;
as: https://github.com/WeiChungChang/ExoPlayer/commit/922529fd1d80ea51dc7bf0938a4960eb2708068c
3. Provide a clearer UI to set playback speed.
as: https://github.com/WeiChungChang/ExoPlayer/commit/938d8e415dfea0a6ee9ff97a4ae98e6720ed4514

## Development history ##
[1. Provide the patch for trick playback when playing adaptive streaming content](#item1)

[2. Propose the gereral solution for high speed & reverse playback funcationality](#item2)

[3. Provide a clearer UI to set playback speed](#item3)

<h2 id="item1">1. Provide the patch for trick playback when playing adaptive streaming content</h2>

Besides setting speed, **I think we should inform the track selection (AdaptiveVideoTrackSelection) also for adaptive streaming case.** **Otherwise, it may lead to rebuffering by wrongly (over) estimated bit-rate.**

You could compare the result below to understand why we should do so.

1. Without the patch, several re-buffering happened: 
https://youtu.be/snDOuQ2yG5E

2. With the patch, NO rebuffering anymore: 
https://youtu.be/xyBz0rHZ5xY

You could check the issue by playbackStatus as:
![rebuffering](https://cloud.githubusercontent.com/assets/14846473/23099290/085809aa-f69e-11e6-8c03-b72a91ae4478.png)

It is, without informing AdaptiveVideoTrackSelection, we will select the representation set whose bit-rate is the maximum one among all representations having smaller bit-rate than current estimated network bandwidth.

For example, by the 4K test link https://www.youtube.com/watch?v=0wCC3aLXdOw, there are 8 representation sets there. The bit-rates from high to low are:
r1. 22876018

r2. 10577952

r3. 4466109

r4. 2354860

r5. 1164538

r6. 631150

r7. 251705

r8. ...
 
For my trial here, the network speed is about 4500000 ~ 5500000. 
Hence we will select r3 = 4466109.
Also, **my HTC X9 device could at most support 4X speedup.**
**Since the video is played by X4, obviously we will run out of buffering data soon. It is why we could see the rebuffering happened.**

**Therefore instead of selecting r3, we should select (4500000/4) ~ (5500000/4) =  1125000 ~ 1375000 by taking speed into consideration. 
It makes the selection locates from r5~r6. 
The test result shows it could avoid the frequent rebuffering issue.**

Please check the patch below.
https://github.com/WeiChungChang/ExoPlayer/commit/5f441187588aa9f5b2b16934bd7fce356155c839

Also, I make a test on my fork at:
https://github.com/WeiChungChang/ExoPlayer/tree/trickPlayBack
Please refer to it.

<h2 id="item2">2. Propose the gereral solution for high speed & reverse playback funcationality</h2>


# Root cause:
**It provides my way to response the call-for-feature of trick playback here** (an ‘acceptable’ way).
It includes:

_**1.Locally reverse playback.**_

_**2.Locally high speed playback (more than X2 (or X4)).**_

The patch could be applied to streaming also. 
**However, I will improve the streaming case hereafter for pending performance issue.**

Patch = 
https://github.com/WeiChungChang/ExoPlayer/commit/922529fd1d80ea51dc7bf0938a4960eb2708068c.

It is based on:
https://github.com/WeiChungChang/ExoPlayer/commit/5f441187588aa9f5b2b16934bd7fce356155c839.
(a fix to include adaptive streaming case)

Here a simple classification to trick playback is given below.

1.a: locally low speed trick playback (ex: <=4 or <=2) (mainly achieved by operating audio or mediaclock speed)

1.b: locally high speed trick playback (ex: >=8) (mainly achieved by seek)

1.c: locally reverse playback (mainly achieved by seek).

2.a: low speed trick playback by streaming(ex: <=4 or <=2).

2.b: high speed trick playback by streaming(ex: >=8).

2.c: reverse playback by streaming.

![1](https://cloud.githubusercontent.com/assets/14846473/23152166/1a5c933e-f83b-11e6-92fb-b324e2374a65.png)

According to this rough classification, **currently there may have been a scheme for 1.a there. Here 1.b & 1.c are tried to be accomplished.**

**Notice that even for X2 local playback, for a movie with 2K resolution, it still gets stuck by I\O bandwidth limit, as demo item 6** 

Test movie: 
1. 2K = https://www.youtube.com/watch?v=iNJdPyoqt8U

2. low bit-rate = https://www.youtube.com/watch?v=3nmnMtbzzjE


# Demo:
1.	**Pure 8X speed normal playback test**: https://youtu.be/7ghbtkWqHVA.
2.	**Pure 8X speed reverse playback test**: https://youtu.be/LM8hbihXCE0.
3.	**Hybrid operations test: 2X->4X->8X->(-8X)->seek(when -8X)->normal playback(X1)->2X->4X->(-8X)->(8X)->normal playback(X1)**: https://youtu.be/3eo158kQGhY 
4.	**Reverse playback (1X) test:** https://youtu.be/QssDhtwo8ZY.
5.	**2K movie, reverse (1X) playback test**: https://youtu.be/4PUF29h4uO8.
6.	**2K movie, not smooth even for X2 or X4 by too high bit-rate test**:
https://youtu.be/0TQptRUerhU.

# The way to implement:
![design](https://cloud.githubusercontent.com/assets/14846473/23158369/ed5cd8b6-f859-11e6-80a9-8981ba543cdc.png)

To implement trick playback by see, we need:
**1. Trigger seek internally.**

**2. Calculate the next seek position.**

**3. Decide the show time of each displayed frame.**

A simple way to implement is to create a standalone MediaClock instance at the control layer. 
Set a default display time by, ex: 500 ms.
When high speed trick playback starts (ex: x8), 

1. we set corresponding time (current playback time) & speed(x8) to StandaloneMediaClock; starts it.

2. deliver the first seek (target is current position).

3. When the frame rendered (it takes some time), arrange the next seek (to be triggered after 500 ms). Set the seek target to (current position + speed *  display time).

Step 3 recursively triggers seek to next position according to the playback speed.
Finally, for fast forward if the target exceeds movie duration or for backward playback the target is less than 0, the playback is complete so we stop delivering next seek.

To evaluate if a design of trick playback is good enough,  the criterion may be:

_**1. If the frames displayed well-demonstrate the critical scene of this movie.**_

_**2. The displayed time is long enough to let the audiences to capture the instance they are interested in.**_

In fact, there are a lot of ways to do it. A recent work could be referred below:
[US20150016804.pdf](https://github.com/google/ExoPlayer/files/789665/US20150016804.pdf)

Since Android is an open system which  is adopted on a lot of devices, it is hard to predict the format (such as GOP setting, key frame interval, bit-rate...) of the movie to be played.
I think it is one of the reasons why iOS could provide much better operation experience than Android.  

At next step, I will include the design for streaming.

<h2 id="item3">Provide a clearer UI to set playback speed</h2>

_**In fact, the patch posted within**_ #https://github.com/WeiChungChang/ExoPlayer/commit/922529fd1d80ea51dc7bf0938a4960eb2708068c

 **_could also serve as an approximate solution to reverse playback; with a little modification._**

Please see the demo below; I tested it on my smart phone.

1. https://youtu.be/47slacQbMug (diving, reverse from 2:07 to the beginning)

2. https://youtu.be/b-nNe6eNR4U (baseball, reverse from 11:30 to 9:00)


The original files are on YouTube:

1. https://www.youtube.com/watch?v=ssY8K2wcxpE

2. https://www.youtube.com/watch?v=wEFx6ly86Jo 


and could be accessed at:

1.https://drive.google.com/open?id=0B7H5vJR3qWj0WjFjclQ5T09GRnM

2.https://drive.google.com/open?id=0B7H5vJR3qWj0MWwwVnRFaXNITGc


**_Simply make the flow tries to DO AS BEST AS IT COULD, as below:_**
`public static final long  TRICK_PLAY_DISPLAY_TIME_MS = 0; /*ms, original it is set to 500*/`

_**The most advantageous aspects of this proposal are:**_

_**1. It DOES NOT NEED re-compression; so it is handy for usage.**_

_**2. It provides a approximate solution to reverse playback; for small files, the result is acceptable.**_

_**3. Even for large file, it still works but in a "seek" mode.**_

_**4. It does NOT make player freeze, stop or hang (such as the case you try to play some 2K (or 4K) movies in X2 by adjusting audio & standalone clock; which means you may need to DO additional checks).**_

We have made a demo UI to facilitate operation.
The patch will be updated soon (after upload has completed) at:
https://github.com/WeiChungChang/ExoPlayer

The demo can bee seen at:

https://youtu.be/s-ODHJjpCBg
https://youtu.be/leKRR93qFec (it is better to see the rewind effect from about 52 seconds the rewind button is pressed: https://youtu.be/leKRR93qFec?t=52)
You could check the rewind functionality (-1X, -2X, -4X, -8X, -16X, -32X) also.

Thanks a lot for @monkey-who's help for UI.


