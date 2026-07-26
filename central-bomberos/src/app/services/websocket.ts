import { Injectable } from '@angular/core';
import { Client } from '@stomp/stompjs';
import { Observable, ReplaySubject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class WebsocketService {
  private stompClient!: Client;
  private reportesSubject: ReplaySubject<any> = new ReplaySubject<any>(1);
  private unidadesSubject: ReplaySubject<any> = new ReplaySubject<any>(1);
  private connected = false;

  constructor() {
    console.log('[WS] WebsocketService constructor');
    this.inicializarConexion();
  }

  private inicializarConexion() {
    console.log('[WS] Iniciando conexion nativa STOMP...');

    this.stompClient = new Client({
      // LA MAGIA ESTÁ AQUÍ: Usamos WebSocket Nativo puro, eliminando SockJS
      brokerURL: 'ws://localhost:8081/ws-emergencias',
      debug: (str) => {
        if (str.includes('CONNECTED') || str.includes('ERROR') || str.includes('SUBSCRIBE')) {
          console.log('[WS-STOMP]', str);
        }
      },
      reconnectDelay: 3000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        this.connected = true;
        console.log('[WS] CONECTADO EXITOSAMENTE a WebSocket Nativo');

        this.stompClient.subscribe('/topic/nuevos-reportes', (message) => {
          if (message.body) {
            try {
              let datos = JSON.parse(message.body);
              if (typeof datos === 'string') {
                datos = JSON.parse(datos);
              }
              this.reportesSubject.next(datos);
            } catch (e) {
              this.reportesSubject.next(message.body);
            }
          }
        });

        this.stompClient.subscribe('/topic/unidades-estado', (message) => {
          if (message.body) {
            try {
              const datos = JSON.parse(message.body);
              this.unidadesSubject.next(datos);
            } catch (e) {
              this.unidadesSubject.next(message.body);
            }
          }
        });
      },
      onStompError: (frame) => {
        console.error('[WS] Error STOMP:', frame.headers['message']);
        this.connected = false;
      },
      onDisconnect: () => {
        console.warn('[WS] Desconectado de WebSocket');
        this.connected = false;
      },
      onWebSocketClose: () => {
        console.warn('[WS] WebSocket cerrado, reconectando...');
        this.connected = false;
      }
    });

    this.stompClient.activate();
  }

  public isConnected(): boolean {
    return this.connected;
  }

  public escucharNuevosReportes(): Observable<any> {
    return this.reportesSubject.asObservable();
  }

  public escucharUnidadesEstado(): Observable<any> {
    return this.unidadesSubject.asObservable();
  }
}