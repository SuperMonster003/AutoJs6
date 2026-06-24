declare module "media" {
  namespace media {
    export type AudioStreamName =
      | "music"
      | "media"
      | "notification"
      | "alarm"
      | "ring"
      | "ringtone"
      | "system"
      | "voice_call"
      | "call"
      | "dtmf";

    export interface AudioStreamInfo {
      readonly schema: "autojs6-node-media-audio-stream-v1";
      readonly stream: string;
      readonly streamType: number;
      readonly volume: number;
      readonly maxVolume: number;
      readonly minVolume: number;
      readonly volumeFixed: boolean;
    }

    export interface MediaModule {
      getAudioStreamVolume(stream?: AudioStreamName, options?: AutoJs6Node.BridgeCallOptions): Promise<number>;
      getAudioStreamMaxVolume(stream?: AudioStreamName, options?: AutoJs6Node.BridgeCallOptions): Promise<number>;
      getAudioStreamInfo(stream?: AudioStreamName, options?: AutoJs6Node.BridgeCallOptions): Promise<AudioStreamInfo>;
    }
  }

  const media: media.MediaModule;
  export = media;
}
