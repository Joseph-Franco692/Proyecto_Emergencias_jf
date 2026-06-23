import { Injectable } from '@angular/core';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { Observable, Subject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class WebsocketService {
  private stompClient!: Client;
  private reportesSubject: Subject<any> = new Subject<any>();
  private unidadesSubject: Subject<any> = new Subject<any>();

  constructor() {
    console.log('🔧 WebsocketService constructor ejecutado');
    this.inicializarConexion();
  }

  private inicializarConexion() {
    console.log('📡 Iniciando configuración de WebSocket...');
    (window as any).global = window;

    this.stompClient = new Client({
      webSocketFactory: () => new SockJS('http://localhost:8081/ws-emergencias'),
      debug: (str) => console.log('STOMP Log:', str),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        console.log('--- ¡CONECTADO CON SOCKJS EN TIEMPO REAL! ---');

        // Topic: nuevos reportes ciudadanos
        this.stompClient.subscribe('/topic/nuevos-reportes', (message) => {
          if (message.body) {
            console.log('--- LLEGÓ UN PAQUETE DESDE EL BACKEND ---', message.body);
            try {
              const datos = JSON.parse(message.body);
              this.reportesSubject.next(datos);
            } catch (e) {
              this.reportesSubject.next(message.body);
            }
          }
        });

        // Topic: cambios de estado de unidades bomberiles (despacho, liberación, llegada)
        this.stompClient.subscribe('/topic/unidades-estado', (message) => {
          if (message.body) {
            console.log('--- EVENTO DE UNIDADES RECIBIDO ---', message.body);
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
        console.error('Error en STOMP Broker: ', frame.headers['message']);
      }
    });

    console.log('🚀 Activando conexión STOMP...');
    this.stompClient.activate();
  }

  /** Observable de nuevos reportes ciudadanos en tiempo real */
  public escucharNuevosReportes(): Observable<any> {
    return this.reportesSubject.asObservable();
  }

  /** Observable de cambios de estado de unidades bomberiles */
  public escucharUnidadesEstado(): Observable<any> {
    return this.unidadesSubject.asObservable();
  }
}