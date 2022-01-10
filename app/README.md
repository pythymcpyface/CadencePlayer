# Cadence Player

This Android app plays tracks from Spotify which have a tempo that matches that of the user's running cadence

## Installation

To install, download the .zip of this project

## Usage
Replace the three variables in the app level build.gradle file shown below with your personal

Help on creating a client ID and redirect uri can be found here: [Spotify Documentation] (https://developer.spotify.com/documentation/)

```gradle
buildConfigField "String", "SPOTIFY_CLIENT_ID", "\"YOUR-CLIENT-ID\""
buildConfigField "String", "SPOTIFY_REDIRECT_URI_PKCE", "\"YOUR-REDIRECT-URI\""
buildConfigField "String", "SPOTIFY_CODE_VERIFIER", "\"YOUR-VERIFIER\""
```

Click the screenshot below for a video demonstrating the app:

<a href="https://github.com/pythymcpyface/CadencePlayer/blob/master/app/Screen%20Recordings/CadencePlayer.mp4">
         <img title="Cadence Player" src="https://github.com/pythymcpyface/CadencePlayer/blob/master/app/Screenshots/Playlists.jpg"
         width="350">
      </a>
