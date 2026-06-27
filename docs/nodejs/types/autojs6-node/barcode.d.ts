declare module "barcode" {
  namespace barcode {
    export type BarcodeFormatName =
      | "ALL"
      | "AZTEC"
      | "CODABAR"
      | "CODE_39"
      | "CODE_93"
      | "CODE_128"
      | "DATA_MATRIX"
      | "EAN_8"
      | "EAN_13"
      | "ITF"
      | "PDF_417"
      | "QR_CODE"
      | "UPC_A"
      | "UPC_E";

    export type BarcodeValueTypeName =
      | "UNKNOWN"
      | "CONTACT_INFO"
      | "EMAIL"
      | "ISBN"
      | "PHONE"
      | "PRODUCT"
      | "SMS"
      | "TEXT"
      | "URL"
      | "WIFI"
      | "GEO"
      | "CALENDAR_EVENT"
      | "DRIVER_LICENSE";

    export interface RegionObject {
      readonly x: number;
      readonly y: number;
      readonly width: number;
      readonly height: number;
    }

    export type Region = readonly [number, number, number, number] | RegionObject;

    export interface PathImageInput {
      readonly type: "path";
      readonly path: string;
    }

    export interface HandleImageInput extends AutoJs6Node.ImageHandleLike {
      readonly type?: "imageHandle";
    }

    export interface CaptureImageInput {
      readonly type: "capture";
      readonly requireExistingPermission: true;
      readonly orientation?: "portrait" | "landscape" | "auto";
    }

    export type BarcodeImageInput = string | PathImageInput | HandleImageInput | CaptureImageInput;

    export interface BarcodeOptions extends AutoJs6Node.BridgeCallOptions {
      region?: Region;
      formats?: readonly BarcodeFormatName[];
      format?: BarcodeFormatName | readonly BarcodeFormatName[];
      qrOnly?: boolean;
      maxResults?: number;
      enableAllPotentialBarcodes?: boolean;
      enableAllPotentialQrCodes?: boolean;
    }

    export interface BarcodeBounds extends AutoJs6Node.Bounds {
      readonly width: number;
      readonly height: number;
    }

    export interface EmailPayload {
      readonly address?: string;
      readonly subject?: string;
      readonly body?: string;
      readonly type?: number;
    }

    export interface PhonePayload {
      readonly number?: string;
      readonly type?: number;
    }

    export interface StructuredPayload {
      readonly payloadType:
        | "email"
        | "phone"
        | "sms"
        | "url"
        | "wifi"
        | "geoPoint"
        | "contactInfo"
        | "calendarEvent"
        | "driverLicense"
        | "text"
        | "unknown";
      readonly email?: EmailPayload;
      readonly phone?: PhonePayload;
      readonly sms?: { readonly message?: string; readonly phoneNumber?: string };
      readonly url?: { readonly title?: string; readonly url?: string };
      readonly wifi?: { readonly ssid?: string; readonly password?: string; readonly encryptionType?: number };
      readonly geoPoint?: { readonly lat: number; readonly lng: number };
      readonly contactInfo?: {
        readonly organization?: string;
        readonly title?: string;
        readonly name?: string;
        readonly phones?: readonly PhonePayload[];
        readonly emails?: readonly EmailPayload[];
        readonly urls?: readonly string[];
        readonly addresses?: readonly string[];
      };
      readonly calendarEvent?: {
        readonly summary?: string;
        readonly description?: string;
        readonly location?: string;
        readonly organizer?: string;
        readonly status?: string;
        readonly start?: string;
        readonly end?: string;
      };
      readonly driverLicense?: {
        readonly documentType?: string;
        readonly firstName?: string;
        readonly middleName?: string;
        readonly lastName?: string;
        readonly licenseNumber?: string;
        readonly gender?: string;
        readonly birthDate?: string;
        readonly expiryDate?: string;
        readonly issuingDate?: string;
        readonly issuingCountry?: string;
        readonly addressStreet?: string;
        readonly addressCity?: string;
        readonly addressState?: string;
        readonly addressZip?: string;
      };
    }

    export interface BarcodeSnapshot {
      readonly schema: "autojs6-node-barcode-snapshot-v1";
      readonly rawValue: string | null;
      readonly displayValue: string | null;
      readonly format: number;
      readonly formatName: BarcodeFormatName;
      readonly valueType: number;
      readonly valueTypeName: BarcodeValueTypeName;
      readonly boundingBox?: BarcodeBounds;
      readonly cornerPoints?: readonly AutoJs6Node.Point[];
      readonly structured?: StructuredPayload;
    }

    export interface BarcodeModule {
      detect(image: BarcodeImageInput, options?: BarcodeOptions): Promise<BarcodeSnapshot | null>;
      detectAll(image: BarcodeImageInput, options?: BarcodeOptions): Promise<readonly BarcodeSnapshot[]>;
      recognizeText(image: BarcodeImageInput, options?: BarcodeOptions): Promise<string | null>;
      recognizeTexts(image: BarcodeImageInput, options?: BarcodeOptions): Promise<readonly string[]>;
    }
  }

  const barcode: barcode.BarcodeModule;
  export = barcode;
}
